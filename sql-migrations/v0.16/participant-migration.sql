-- ============================================================
-- EDC v0.15.1 → v0.16.0: Participant database migration
-- Target databases: consumerdb, providerdb
--
-- Tables modified:
--   edc_transfer_process  — add data_address_alias (PR #5489, DataAddress moved to Vault)
--   participant_context   — add identity; fold api_token_alias + roles into properties
--                           (IdentityHub's participant-context store was replaced by the
--                            Connector one in identityhub-feature-sql-bom 0.16.0)
--
-- MIGRATION STRATEGY: additive only.
--   Old columns (did, roles, api_token_alias, resource_manifest,
--   provisioned_resource_set, deprovisioned_resources) are deliberately KEPT so that a
--   rollback to v0.15.1 remains possible. Data is copied, never moved.
--   See rollback.sql for the reverse direction.
--
-- Idempotent: safe to re-run.
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- edc_transfer_process: add data_address_alias
--
-- EDC 0.16.0 stores the TransferProcess DataAddress in the Vault and keeps only a short
-- alias in the DB (PR #5489). Column type matches EDC's own transfer-process-schema.sql
-- exactly (TEXT, no index).
--
-- NOTE: the three provisioner columns removed from EDC's 0.16.0 schema
-- (resource_manifest, provisioned_resource_set, deprovisioned_resources) are intentionally
-- NOT dropped here. They are nullable and ignored by 0.16.0, and keeping them preserves
-- the rollback path.
-- ────────────────────────────────────────────────────────────
ALTER TABLE IF EXISTS edc_transfer_process
    ADD COLUMN IF NOT EXISTS data_address_alias TEXT;

-- ────────────────────────────────────────────────────────────
-- participant_context: IdentityHub → Connector store migration
--
-- v0.15.1 (IdentityHub identity-hub-participantcontext-store-sql):
--     participant_context_id, created_date, last_modified_date, state,
--     api_token_alias VARCHAR NOT NULL, did VARCHAR, roles JSON, properties JSON
--
-- v0.16.0 (Connector participantcontext-store-sql):
--     participant_context_id, identity VARCHAR UNIQUE NOT NULL,
--     created_date, last_modified_date, state, properties JSON
--
-- At 0.16.0 IdentityHubParticipantContext extends ParticipantContext and relocates fields:
--     getDid()           -> getIdentity()                     (identity column)
--     getApiTokenAlias() -> properties['apiTokenAlias']
--     getRoles()         -> properties['roles']
-- Builder.build() requires non-null apiTokenAlias AND did, so without this migration the
-- IdentityHub runtime fails while loading existing participant contexts.
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'participant_context') THEN
        RAISE NOTICE 'participant_context table not present — skipping';
        RETURN;
    END IF;

    -- 1. add the identity column expected by the Connector store
    ALTER TABLE participant_context
        ADD COLUMN IF NOT EXISTS identity character varying;

    -- 2. backfill identity from the legacy did column (did IS the identity at 0.16.0)
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'did') THEN
        UPDATE participant_context
           SET identity = did
         WHERE identity IS NULL
           AND did IS NOT NULL;
    END IF;

    -- 3. copy api_token_alias + roles into the properties JSON document.
    --    Guarded so that re-runs cannot clobber rows already written by 0.16.0
    --    (those have properties['apiTokenAlias'] set and api_token_alias NULL).
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'api_token_alias') THEN
        UPDATE participant_context
           SET properties = (
                   COALESCE(properties::jsonb, '{}'::jsonb)
                   || jsonb_build_object('apiTokenAlias', api_token_alias)
                   || jsonb_build_object('roles', COALESCE(roles::jsonb, '[]'::jsonb))
               )::json
         WHERE api_token_alias IS NOT NULL
           AND (properties::jsonb -> 'apiTokenAlias') IS NULL;
    END IF;

    -- 4. api_token_alias is NOT NULL in the v0.15.1 schema, but the Connector store never
    --    writes it. Without dropping the constraint, every new participant context INSERT
    --    fails. The column itself is kept for rollback.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context'
                 AND column_name = 'api_token_alias'
                 AND is_nullable = 'NO') THEN
        ALTER TABLE participant_context ALTER COLUMN api_token_alias DROP NOT NULL;
    END IF;

    -- 5. remap the state column.
    --    IdentityHub 0.15.1's ParticipantContextState had no explicit codes, so the store
    --    persisted the enum ORDINAL (0=CREATED, 1=ACTIVATED, 2=DEACTIVATED — see the comment
    --    in the v0.15.1 schema). Connector 0.16.0's enum declares explicit codes
    --    (CREATED=100, ACTIVATED=200, DEACTIVATED=300) and resolves them with
    --        from(code) -> filter(pcs.code == code).findFirst().orElse(null)
    --    so a legacy ordinal resolves to null and SqlParticipantContextStore.mapResultSet
    --    throws NullPointerException on ParticipantContextState.code().
    --    Idempotent: 100/200/300 are outside the legacy 0..2 range, so re-runs are no-ops.
    UPDATE participant_context SET state = CASE state
        WHEN 0 THEN 100    -- CREATED
        WHEN 1 THEN 200    -- ACTIVATED
        WHEN 2 THEN 300    -- DEACTIVATED
        ELSE state
    END
    WHERE state IN (0, 1, 2);
END $$;

-- 5. uniqueness on identity, matching EDC's fresh schema.
--    Postgres permits multiple NULLs in a unique index, so this is safe even if some row
--    has no did. NOT NULL is deliberately NOT enforced: a migrated database may legitimately
--    contain a context with no did yet, and failing the migration there would be worse.
CREATE UNIQUE INDEX IF NOT EXISTS participant_context_identity_uindex
    ON participant_context (identity);

COMMIT;
