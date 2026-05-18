-- ============================================================
-- EDC v0.14 → v0.15.1: Participant database migration
-- Target databases: consumerdb, providerdb
--
-- Tables modified:
--   edc_asset                — add participant_context_id
--   edc_policydefinitions    — add participant_context_id
--   edc_contract_definitions — add participant_context_id
--   edc_contract_negotiation — add participant_context_id
--   edc_contract_agreement   — add participant_context_id
--   edc_transfer_process     — add participant_context_id, dataplane_metadata
--   edc_edr_entry            — add participant_context_id
--   edc_data_plane_instance  — add participant_context_id
--   credential_resource      — add vc_usage column
--   participant_context      — add state_timestamp column
--   edc_lease                — add resource_kind column
--   edc_telemetry_record     — add inline lease columns
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- edc_asset: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_asset
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_asset
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_asset
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_asset_participant_context_id
    ON edc_asset (participant_context_id);

-- ────────────────────────────────────────────────────────────
-- edc_policydefinitions: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_policydefinitions
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_policydefinitions
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_policydefinitions
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_policydefinitions_participant_context_id
    ON edc_policydefinitions (participant_context_id);

-- ────────────────────────────────────────────────────────────
-- edc_contract_definitions: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_contract_definitions
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_contract_definitions
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_contract_definitions
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_contract_definitions_participant_context_id
    ON edc_contract_definitions (participant_context_id);

-- ────────────────────────────────────────────────────────────
-- edc_contract_negotiation: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_contract_negotiation
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_contract_negotiation
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_contract_negotiation
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_contract_negotiation_participant_context_id
    ON edc_contract_negotiation (participant_context_id);

-- ────────────────────────────────────────────────────────────
-- edc_contract_agreement: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_contract_agreement
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_contract_agreement
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_contract_agreement
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_contract_agreement_participant_context_id
    ON edc_contract_agreement (participant_context_id);

-- ────────────────────────────────────────────────────────────
-- edc_transfer_process: add participant_context_id + dataplane_metadata
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_transfer_process
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_transfer_process
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_transfer_process
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_transfer_process_participant_context_id
    ON edc_transfer_process (participant_context_id);

ALTER TABLE edc_transfer_process
    ADD COLUMN IF NOT EXISTS dataplane_metadata json;

-- ────────────────────────────────────────────────────────────
-- edc_edr_entry: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_edr_entry
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_edr_entry
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_edr_entry
    ALTER COLUMN participant_context_id SET NOT NULL;

-- ────────────────────────────────────────────────────────────
-- edc_data_plane_instance: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_data_plane_instance
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_data_plane_instance
    SET participant_context_id = 'default-participant'
    WHERE participant_context_id IS NULL;

ALTER TABLE edc_data_plane_instance
    ALTER COLUMN participant_context_id SET NOT NULL;

ALTER TABLE edc_data_plane_instance
    ALTER COLUMN participant_context_id SET DEFAULT 'default-participant';

-- ────────────────────────────────────────────────────────────
-- credential_resource: add vc_usage column (table exists in participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'credential_resource') THEN
        ALTER TABLE credential_resource ADD COLUMN IF NOT EXISTS vc_usage character varying;
        UPDATE credential_resource SET vc_usage = 'Holder' WHERE vc_usage IS NULL;
        ALTER TABLE credential_resource ALTER COLUMN vc_usage SET NOT NULL;
        ALTER TABLE credential_resource ALTER COLUMN vc_usage SET DEFAULT 'Holder';
        CREATE INDEX IF NOT EXISTS idx_credential_resource_vc_usage ON credential_resource (vc_usage);
        UPDATE credential_resource SET vc_format = 1 WHERE vc_format = 3;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- participant_context: add state_timestamp column (table exists in participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'participant_context') THEN
        ALTER TABLE participant_context ADD COLUMN IF NOT EXISTS state_timestamp bigint;
        UPDATE participant_context SET state_timestamp = EXTRACT(EPOCH FROM NOW())::bigint * 1000 WHERE state_timestamp IS NULL;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_lease: add resource_kind column and re-create PK on lease_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_lease
    DROP CONSTRAINT IF EXISTS edc_lease_pkey;

ALTER TABLE edc_lease
    ADD COLUMN IF NOT EXISTS resource_kind character varying NOT NULL DEFAULT 'default';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'edc_lease_pkey'
          AND conrelid = 'edc_lease'::regclass
    ) THEN
        ALTER TABLE edc_lease ADD PRIMARY KEY (lease_id);
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_telemetry_record: add inline lease columns
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_telemetry_record
    ADD COLUMN IF NOT EXISTS leased_by character varying;

ALTER TABLE edc_telemetry_record
    ADD COLUMN IF NOT EXISTS leased_at bigint;

ALTER TABLE edc_telemetry_record
    ADD COLUMN IF NOT EXISTS lease_duration integer;

COMMIT;
