-- ============================================================
-- ROLLBACK: EDC v0.18.1 → v0.16.1
-- Target databases: consumerdb, providerdb, authoritydb
--
-- Nothing to do for the binaries: every column added by participant-migration.sql and
-- authority-migration.sql is nullable or has a default and is ignored by EDC 0.16.1, so the
-- v0.16.1 runtimes start on the migrated schema as-is. Keep the columns.
--
-- Data written while running v0.18.1 that v0.16.1 cannot see:
--   * claims on agreements / transfer processes (informational, not needed by 0.16.1)
--   * edc_participant_context_config rows (0.16.1 kept this config in memory and re-seeds it)
--   * edc_accesstokendata / edc_data_plane (hashicorp data-plane fix): 0.16.1 hashicorp data planes ignore them and go
--     back to in-memory stores. Keep them: re-upgrading then finds the persisted tokens/flows again
-- Transfer process data destinations: DSE pins the legacy data plane signaling, so 0.18.1 keeps
-- writing edc_transfer_process.data_destination as 0.16.1 expects.
--
-- Only if the schema itself must be reverted (e.g. restoring an exact 0.16.1 schema), run the
-- statements below manually. They DROP data and are therefore commented out on purpose.
-- ============================================================

-- ALTER TABLE IF EXISTS edc_contract_agreement DROP COLUMN IF EXISTS claims;
-- ALTER TABLE IF EXISTS edc_transfer_process   DROP COLUMN IF EXISTS claims;
-- ALTER TABLE IF EXISTS edc_transfer_process   DROP COLUMN IF EXISTS data_address_owner;
-- ALTER TABLE IF EXISTS credential_definitions DROP COLUMN IF EXISTS additional_context;

SELECT 'rollback.sql: no-op by design, see header' AS info;
