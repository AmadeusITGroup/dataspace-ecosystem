-- ============================================================
-- EDC v0.16.1 → v0.18.1: Participant database migration
-- Target databases: consumerdb, providerdb
--
-- Tables modified:
--   edc_contract_agreement — add claims             (Connector #5626/#5727, counter-party claims on agreements)
--   edc_transfer_process   — add claims             (Connector #5631/#5632, counter-party claims on transfers)
--                            add data_address_owner (Connector #5709, DPS suspend/resume)
--
-- Why a script is needed: the EDC SQL stores read and write these columns unconditionally,
-- and schema bootstrapping (EDC_SQL_SCHEMA_AUTOCREATE) only runs CREATE TABLE IF NOT EXISTS,
-- which never adds columns to an existing table. Without this script the first negotiation or
-- transfer after the upgrade fails at runtime.
--
-- Verified scope (git diff v0.16.1 v0.18.1 -- '*.sql' in Connector, v0.16.0..v0.18.1 in
-- IdentityHub, Technology-Azure v0.16.0..v0.18.0): no other table used by a participant
-- runtime changes. New tables (edc_participant_context_config) are created by bootstrapping.
-- No persisted enum/state code changed (TransferProcessStates only gains RESUMING_REQUESTED=722).
--
-- MIGRATION STRATEGY: additive only, nullable/defaulted columns. EDC 0.16.1 ignores them, so a
-- rollback of the binaries works without touching the schema (see rollback.sql).
-- Types match EDC's contract-negotiation-schema.sql / transfer-process-schema.sql at v0.18.1.
--
-- Idempotent: safe to re-run. Run BEFORE starting the v0.18.1 binaries.
-- ============================================================
BEGIN;

ALTER TABLE IF EXISTS edc_contract_agreement
    ADD COLUMN IF NOT EXISTS claims JSON;

ALTER TABLE IF EXISTS edc_transfer_process
    ADD COLUMN IF NOT EXISTS claims JSON;

ALTER TABLE IF EXISTS edc_transfer_process
    ADD COLUMN IF NOT EXISTS data_address_owner BOOLEAN DEFAULT FALSE;

-- Data-plane stores (hashicorp data-plane launcher fix): from this release the hashicorp data plane persists
-- access tokens (edc_accesstokendata) and data flows (edc_data_plane) in the participant DB. They are NOT created
-- here on purpose: this script runs as a DB superuser, and tables created by it would be owned by that superuser,
-- so the data plane's schema bootstrap (EDC_SQL_SCHEMA_AUTOCREATE=true, it runs COMMENT ON COLUMN, which needs
-- ownership) fails at boot with "must be owner of relation edc_accesstokendata". The data plane creates them itself,
-- owned by the participant DB user. If autocreate is disabled in an environment, create them AS the participant user
-- with the upstream 0.18.1 files accesstoken-data-schema.sql and dataplane-schema.sql.

COMMIT;

