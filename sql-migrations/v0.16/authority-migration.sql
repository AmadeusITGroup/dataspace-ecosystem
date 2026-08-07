-- ============================================================
-- EDC v0.15.1 → v0.16.0: Authority database migration
-- Target database: authoritydb  (IdentityHub + Issuer Service tables)
--
-- Tables modified:
--   participant_context   — add identity; fold api_token_alias + roles into properties
--
-- Verified scope: the ONLY schema deltas between EDC 0.15.1 and 0.16.0 are
--   * Connector  : transfer-process-schema.sql (data_address_alias — participant DBs only)
--   * IdentityHub: identity-hub-participantcontext-store-sql REMOVED, replaced by the
--                  Connector participantcontext-store-sql in identityhub-feature-sql-bom
--   * Connector  : new cel-expression-schema.sql (created by schema bootstrapping,
--                  no migration required)
-- All other IdentityHub / Issuer Service tables are unchanged.
--
-- MIGRATION STRATEGY: additive only — old columns kept so rollback stays possible.
-- Idempotent: safe to re-run.
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- participant_context: IdentityHub → Connector store migration
--
-- v0.15.1 (IdentityHub): api_token_alias VARCHAR NOT NULL, did VARCHAR, roles JSON
-- v0.16.0 (Connector)  : identity VARCHAR UNIQUE NOT NULL, properties JSON
--
-- IdentityHubParticipantContext at 0.16.0:
--   getDid()           -> getIdentity()               (identity column)
--   getApiTokenAlias() -> properties['apiTokenAlias']
--   getRoles()         -> properties['roles']
--
-- This is the table the superuser seed (ParticipantContextSeedExtension) touches on every
-- IdentityHub startup, so a missed migration surfaces immediately as a boot failure.
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

    -- 2. backfill identity from the legacy did column
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'did') THEN
        UPDATE participant_context
           SET identity = did
         WHERE identity IS NULL
           AND did IS NOT NULL;
    END IF;

    -- 3. copy api_token_alias + roles into properties JSON.
    --    Guarded so re-runs cannot clobber rows already written by 0.16.0.
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

    -- 4. drop the NOT NULL on api_token_alias — the Connector store never writes it, so new
    --    inserts would otherwise fail. Column retained for rollback.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context'
                 AND column_name = 'api_token_alias'
                 AND is_nullable = 'NO') THEN
        ALTER TABLE participant_context ALTER COLUMN api_token_alias DROP NOT NULL;
    END IF;

    -- 5. remap the state column.
    --    IdentityHub 0.15.1 stored the enum ORDINAL (0=CREATED, 1=ACTIVATED, 2=DEACTIVATED);
    --    Connector 0.16.0 uses explicit codes (100/200/300) and its from(code) lookup returns
    --    null for a legacy ordinal, which makes SqlParticipantContextStore.mapResultSet throw
    --    NullPointerException on ParticipantContextState.code().
    --    Idempotent: 100/200/300 fall outside the legacy 0..2 range.
    UPDATE participant_context SET state = CASE state
        WHEN 0 THEN 100    -- CREATED
        WHEN 1 THEN 200    -- ACTIVATED
        WHEN 2 THEN 300    -- DEACTIVATED
        ELSE state
    END
    WHERE state IN (0, 1, 2);
END $$;

-- 5. uniqueness on identity (NULLs permitted; NOT NULL deliberately not enforced)
CREATE UNIQUE INDEX IF NOT EXISTS participant_context_identity_uindex
    ON participant_context (identity);

COMMIT;
