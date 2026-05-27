-- ============================================================
-- EDC v0.14 → v0.15.1: Participant database migration
-- Target databases: consumerdb, providerdb
--
-- Tables modified:
--   edc_asset                — add participant_context_id
--   edc_policydefinitions    — add participant_context_id
--   edc_contract_definitions — add participant_context_id
--   edc_contract_negotiation — add participant_context_id
--   edc_contract_agreement   — add agr_participant_context_id, agr_agreement_id + unique constraint
--   edc_transfer_process     — add participant_context_id, dataplane_metadata
--   edc_edr_entry            — add participant_context_id
--   edc_data_plane_instance  — add participant_context_id
--   credential_resource      — rename vc_usage → usage
--   participant_context      — add state_timestamp column
--   credential_definitions   — replace formats array with format column
--   edc_holder_credentialrequest — transform types_and_formats to ids_and_formats
--   holders                  — add anonymous + properties columns
--   edc_lease                — replace lease_id with resource_id + resource_kind PK
--   edc_sts_client           — add participant_context_id
--   keypair_resource         — add usage column
--   edc_telemetry_record     — add inline lease columns
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- edc_asset: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_asset
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_asset
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

ALTER TABLE edc_asset
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_asset_participant_context_id
    ON edc_asset (participant_context_id);

ALTER TABLE edc_asset
    ADD COLUMN IF NOT EXISTS dataplane_metadata json;

-- ────────────────────────────────────────────────────────────
-- edc_policydefinitions: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_policydefinitions
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_policydefinitions
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

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
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

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
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

ALTER TABLE edc_contract_negotiation
    ALTER COLUMN participant_context_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edc_contract_negotiation_participant_context_id
    ON edc_contract_negotiation (participant_context_id);

CREATE INDEX IF NOT EXISTS contract_negotiation_agreement_id_index
    ON edc_contract_negotiation (agreement_id);

-- ────────────────────────────────────────────────────────────
-- edc_contract_agreement: add agr_participant_context_id & agr_agreement_id
-- ────────────────────────────────────────────────────────────
-- Rename participant_context_id → agr_participant_context_id if old name exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'edc_contract_agreement' AND column_name = 'participant_context_id') THEN
        ALTER TABLE edc_contract_agreement RENAME COLUMN participant_context_id TO agr_participant_context_id;
    END IF;
END $$;

ALTER TABLE edc_contract_agreement
    ADD COLUMN IF NOT EXISTS agr_participant_context_id character varying;

UPDATE edc_contract_agreement
    SET agr_participant_context_id = '${participant_did}'
    WHERE agr_participant_context_id IS NULL OR agr_participant_context_id = 'default-participant';

ALTER TABLE edc_contract_agreement
    ALTER COLUMN agr_participant_context_id SET NOT NULL;

-- agr_agreement_id: new in v0.15, populated from agr_id
ALTER TABLE edc_contract_agreement
    ADD COLUMN IF NOT EXISTS agr_agreement_id character varying;

UPDATE edc_contract_agreement
    SET agr_agreement_id = agr_id
    WHERE agr_agreement_id IS NULL;

ALTER TABLE edc_contract_agreement
    ALTER COLUMN agr_agreement_id SET NOT NULL;

-- Add unique constraint (idempotent via DO block)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'edc_contract_agreement_agr_agreement_id_agr_participant_co_key'
                     AND conrelid = 'edc_contract_agreement'::regclass) THEN
        ALTER TABLE edc_contract_agreement ADD UNIQUE (agr_agreement_id, agr_participant_context_id);
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_transfer_process: add participant_context_id + dataplane_metadata
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_transfer_process
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_transfer_process
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

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
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

ALTER TABLE edc_edr_entry
    ALTER COLUMN participant_context_id SET NOT NULL;

-- ────────────────────────────────────────────────────────────
-- edc_data_plane_instance: add participant_context_id
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_data_plane_instance
    ADD COLUMN IF NOT EXISTS participant_context_id character varying;

UPDATE edc_data_plane_instance
    SET participant_context_id = '${participant_did}'
    WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

ALTER TABLE edc_data_plane_instance
    ALTER COLUMN participant_context_id SET NOT NULL;

ALTER TABLE edc_data_plane_instance
    ALTER COLUMN participant_context_id SET DEFAULT '${participant_did}';

-- ────────────────────────────────────────────────────────────
-- credential_resource: add usage column (v0.15 expects 'usage', not 'vc_usage')
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'credential_resource') THEN
        -- Rename vc_usage → usage if old name exists
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'credential_resource' AND column_name = 'vc_usage') THEN
            ALTER TABLE credential_resource RENAME COLUMN vc_usage TO usage;
        END IF;
        ALTER TABLE credential_resource ADD COLUMN IF NOT EXISTS usage character varying;
        UPDATE credential_resource SET usage = 'Holder' WHERE usage IS NULL;
        ALTER TABLE credential_resource ALTER COLUMN usage SET NOT NULL;
        ALTER TABLE credential_resource ALTER COLUMN usage SET DEFAULT 'Holder';
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
-- credential_definitions: replace formats array with format column
-- (IH table, may not exist in all participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'credential_definitions') THEN
        ALTER TABLE credential_definitions ADD COLUMN IF NOT EXISTS format character varying;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'credential_definitions' AND column_name = 'formats'
        ) THEN
            UPDATE credential_definitions
                SET format = (formats ->> 0)
                WHERE formats IS NOT NULL
                  AND formats::text != '[]'
                  AND format IS NULL;
        END IF;

        UPDATE credential_definitions SET format = 'VC1_0_JWT' WHERE format IS NULL;
        ALTER TABLE credential_definitions ALTER COLUMN format SET NOT NULL;
        ALTER TABLE credential_definitions DROP COLUMN IF EXISTS formats;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_holder_credentialrequest: transform types_and_formats → ids_and_formats
-- (IH table, may not exist in all participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'edc_holder_credentialrequest') THEN
        ALTER TABLE edc_holder_credentialrequest ADD COLUMN IF NOT EXISTS ids_and_formats json;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'edc_holder_credentialrequest' AND column_name = 'types_and_formats'
        ) THEN
            UPDATE edc_holder_credentialrequest
                SET ids_and_formats = (
                    SELECT json_agg(
                        json_build_object(
                            'id', CASE
                                WHEN key = 'MembershipCredential' THEN 'membership-credential-def-1'
                                WHEN key = 'DomainCredential' THEN 'domain-credential-def-1'
                                ELSE lower(key)
                            END,
                            'credentialType', key,
                            'format', value
                        )
                    )
                    FROM json_each_text(types_and_formats)
                )
                WHERE types_and_formats IS NOT NULL
                  AND types_and_formats::text != '{}'
                  AND json_typeof(types_and_formats) = 'object'
                  AND ids_and_formats IS NULL;
        END IF;

        UPDATE edc_holder_credentialrequest SET ids_and_formats = '[]'::json WHERE ids_and_formats IS NULL;
        ALTER TABLE edc_holder_credentialrequest ALTER COLUMN ids_and_formats SET NOT NULL;
        ALTER TABLE edc_holder_credentialrequest DROP COLUMN IF EXISTS types_and_formats;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- holders: add anonymous + properties columns
-- (IH table, may not exist in all participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'holders') THEN
        ALTER TABLE holders ADD COLUMN IF NOT EXISTS anonymous BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE holders ADD COLUMN IF NOT EXISTS properties JSON DEFAULT '{}'::json;
        UPDATE holders SET properties = '{}'::json WHERE properties IS NULL;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_lease: replace lease_id with resource_id + resource_kind
-- ────────────────────────────────────────────────────────────
-- Drop ALL foreign keys referencing edc_lease before restructuring
DO $$
DECLARE
    fk_rec RECORD;
BEGIN
    FOR fk_rec IN
        SELECT tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu
            ON tc.constraint_name = ccu.constraint_name
            AND tc.table_schema = ccu.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND ccu.table_name = 'edc_lease'
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I',
                       fk_rec.table_name, fk_rec.constraint_name);
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edc_lease'
          AND column_name = 'lease_id'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edc_lease'
          AND column_name = 'resource_id'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edc_lease'
          AND column_name = 'resource_kind'
    ) THEN
        EXECUTE 'TRUNCATE TABLE edc_lease';
    END IF;
END $$;

DROP INDEX IF EXISTS lease_lease_id_uindex;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edc_lease'
          AND column_name = 'lease_id'
    ) THEN
        ALTER TABLE edc_lease DROP CONSTRAINT IF EXISTS lease_pk;
        ALTER TABLE edc_lease DROP CONSTRAINT IF EXISTS edc_lease_pkey;
        ALTER TABLE edc_lease DROP COLUMN IF EXISTS lease_id;
    END IF;
END $$;

ALTER TABLE edc_lease
    ADD COLUMN IF NOT EXISTS resource_id VARCHAR;

ALTER TABLE edc_lease
    ADD COLUMN IF NOT EXISTS resource_kind VARCHAR;

ALTER TABLE edc_lease
    ALTER COLUMN resource_id SET NOT NULL;

ALTER TABLE edc_lease
    ALTER COLUMN resource_kind SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'edc_lease'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE edc_lease
            ADD CONSTRAINT edc_lease_pkey PRIMARY KEY (resource_id, resource_kind);
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- edc_sts_client: add participant_context_id
-- (IH table, may not exist in all participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'edc_sts_client') THEN
        ALTER TABLE edc_sts_client ADD COLUMN IF NOT EXISTS participant_context_id VARCHAR;

        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'participant_context') THEN
            UPDATE edc_sts_client AS sc
            SET participant_context_id = pc.participant_context_id
            FROM participant_context AS pc
            WHERE sc.participant_context_id IS NULL
              AND pc.did = sc.did;
        END IF;

        -- If still NULL after backfill, use did as fallback
        UPDATE edc_sts_client SET participant_context_id = did WHERE participant_context_id IS NULL OR participant_context_id = 'default-participant';

        ALTER TABLE edc_sts_client ALTER COLUMN participant_context_id SET NOT NULL;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────
-- keypair_resource: add usage column
-- (IH table, may not exist in all participant DBs)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'keypair_resource') THEN
        ALTER TABLE keypair_resource ADD COLUMN IF NOT EXISTS usage VARCHAR;
        UPDATE keypair_resource SET usage = '["sign_credentials","sign_presentation","sign_token"]' WHERE usage IS NULL;
        ALTER TABLE keypair_resource ALTER COLUMN usage SET NOT NULL;
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

-- ────────────────────────────────────────────────────────────
-- did_resources: update DSPMessaging endpoint to include /2025-1 protocol version
-- (idempotent: only updates if /2025-1 not already present)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'did_resources') THEN
        UPDATE did_resources
        SET did_document = jsonb_set(
            did_document::jsonb,
            '{service}',
            (
                SELECT jsonb_agg(
                    CASE
                        WHEN elem->>'type' = 'DSPMessaging'
                        THEN jsonb_set(elem::jsonb, '{serviceEndpoint}',
                             to_jsonb(elem->>'serviceEndpoint' || '/2025-1'))
                        ELSE elem::jsonb
                    END
                )
                FROM jsonb_array_elements(did_document::jsonb->'service') AS elem
            )
        )::json
        WHERE did_document::text LIKE '%DSPMessaging%'
          AND did_document::text NOT LIKE '%/2025-1%';
    END IF;
END $$;

COMMIT;
