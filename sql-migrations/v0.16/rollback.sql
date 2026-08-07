-- ============================================================
-- ROLLBACK: EDC v0.16.0 → v0.15.1
-- Target databases: consumerdb, providerdb, authoritydb
--
-- The forward migration (participant-migration.sql / authority-migration.sql) is additive:
-- it copies data into new locations and never deletes the old columns. So rolling the
-- binaries back to v0.15.1 is safe for every row that existed BEFORE the upgrade.
--
-- This script handles the remaining case: rows CREATED or UPDATED while running v0.16.0.
-- The Connector participant-context store only writes
--     participant_context_id, identity, created_date, last_modified_date, state, properties
-- so for those rows the legacy columns (did, api_token_alias, roles) are NULL, and v0.15.1's
-- IdentityHubParticipantContext.Builder.build() would fail its requireNonNull checks.
-- Here we copy the values back out of the properties JSON into the legacy columns.
--
-- Idempotent: safe to re-run. Run this BEFORE starting the v0.15.1 binaries.
-- ============================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'participant_context') THEN
        RAISE NOTICE 'participant_context table not present — skipping';
        RETURN;
    END IF;

    -- 1. did ← identity (for rows written by 0.16.0, which never populated did)
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'did') THEN
        UPDATE participant_context
           SET did = identity
         WHERE did IS NULL
           AND identity IS NOT NULL;
    END IF;

    -- 2. api_token_alias ← properties['apiTokenAlias']
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'api_token_alias') THEN
        UPDATE participant_context
           SET api_token_alias = properties::jsonb ->> 'apiTokenAlias'
         WHERE api_token_alias IS NULL
           AND (properties::jsonb -> 'apiTokenAlias') IS NOT NULL;
    END IF;

    -- 3. roles ← properties['roles']
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'roles') THEN
        UPDATE participant_context
           SET roles = (properties::jsonb -> 'roles')::json
         WHERE roles IS NULL
           AND (properties::jsonb -> 'roles') IS NOT NULL;
    END IF;

    -- 4. reverse the state remapping applied by the forward migration.
    --    Connector 0.16.0 codes (100/200/300) are meaningless to IdentityHub 0.15.1, which
    --    persisted the enum ORDINAL (0/1/2). Idempotent: 0..2 are outside the 0.16.0 range.
    UPDATE participant_context SET state = CASE state
        WHEN 100 THEN 0    -- CREATED
        WHEN 200 THEN 1    -- ACTIVATED
        WHEN 300 THEN 2    -- DEACTIVATED
        ELSE state
    END
    WHERE state IN (100, 200, 300);

    -- 5. restore the NOT NULL constraint that v0.15.1's schema declares.
    --    Only possible once every row has an api_token_alias; otherwise leave it nullable
    --    and report, rather than failing the rollback.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'participant_context' AND column_name = 'api_token_alias') THEN
        IF NOT EXISTS (SELECT 1 FROM participant_context WHERE api_token_alias IS NULL) THEN
            ALTER TABLE participant_context ALTER COLUMN api_token_alias SET NOT NULL;
        ELSE
            RAISE WARNING 'participant_context has rows with NULL api_token_alias; '
                          'leaving column nullable. Inspect these rows before starting v0.15.1.';
        END IF;
    END IF;
END $$;

-- 6. drop the index added by the forward migration (the column itself is harmless to keep,
--    v0.15.1 simply ignores it)
DROP INDEX IF EXISTS participant_context_identity_uindex;

-- NOTE on edc_transfer_process.data_address_alias:
--   Deliberately NOT dropped. v0.15.1 ignores the column. Because DSE runs the legacy data
--   plane signaling module, VaultDataAddressStore keeps writing data_destination alongside
--   the Vault copy (dataPlaneProtocolInUse.isLegacy() == true), so transfer processes created
--   under 0.16.0 remain readable by v0.15.1 without any further action.

COMMIT;
