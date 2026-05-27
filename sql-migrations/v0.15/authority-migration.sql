-- ============================================================
-- EDC v0.14 → v0.15.1: Authority database migration
-- Target database: authoritydb
--
-- Tables modified:
--   credential_resource        — rename vc_usage → usage
--   participant_context        — add state_timestamp column
--   credential_definitions     — replace formats array with format column
--   edc_holder_credentialrequest — transform types_and_formats to ids_and_formats
--   holders                    — add anonymous + properties columns
--   edc_lease                  — replace lease_id with resource_id + resource_kind PK
--   edc_sts_client             — add participant_context_id
--   keypair_resource           — add usage column
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- credential_resource: add usage column (v0.15 expects 'usage', not 'vc_usage')
-- ────────────────────────────────────────────────────────────
-- Rename vc_usage → usage if old name exists (idempotent for re-runs)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'credential_resource' AND column_name = 'vc_usage') THEN
        ALTER TABLE credential_resource RENAME COLUMN vc_usage TO usage;
    END IF;
END $$;

ALTER TABLE credential_resource
    ADD COLUMN IF NOT EXISTS usage character varying;

UPDATE credential_resource
    SET usage = 'Holder'
    WHERE usage IS NULL;

ALTER TABLE credential_resource
    ALTER COLUMN usage SET NOT NULL;

ALTER TABLE credential_resource
    ALTER COLUMN usage SET DEFAULT 'Holder';

-- credential_resource: update vc_format value (3 -> 1 for JWT)
UPDATE credential_resource
    SET vc_format = 1
    WHERE vc_format = 3;

-- ────────────────────────────────────────────────────────────
-- participant_context: add state_timestamp column
-- ────────────────────────────────────────────────────────────
ALTER TABLE participant_context
    ADD COLUMN IF NOT EXISTS state_timestamp bigint;

UPDATE participant_context
    SET state_timestamp = EXTRACT(EPOCH FROM NOW())::bigint * 1000
    WHERE state_timestamp IS NULL;

-- ────────────────────────────────────────────────────────────
-- credential_definitions: replace formats array with format column
-- ────────────────────────────────────────────────────────────
ALTER TABLE credential_definitions
    ADD COLUMN IF NOT EXISTS format character varying;

DO $$
BEGIN
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
END $$;

UPDATE credential_definitions
    SET format = 'VC1_0_JWT'
    WHERE format IS NULL;

ALTER TABLE credential_definitions
    ALTER COLUMN format SET NOT NULL;

ALTER TABLE credential_definitions
    DROP COLUMN IF EXISTS formats;

-- ────────────────────────────────────────────────────────────
-- edc_holder_credentialrequest: transform types_and_formats → ids_and_formats
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_holder_credentialrequest
    ADD COLUMN IF NOT EXISTS ids_and_formats json;

DO $$
BEGIN
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
END $$;

UPDATE edc_holder_credentialrequest
    SET ids_and_formats = '[]'::json
    WHERE ids_and_formats IS NULL;

ALTER TABLE edc_holder_credentialrequest
    ALTER COLUMN ids_and_formats SET NOT NULL;

ALTER TABLE edc_holder_credentialrequest
    DROP COLUMN IF EXISTS types_and_formats;

-- ────────────────────────────────────────────────────────────
-- holders: add anonymous + properties columns
-- ────────────────────────────────────────────────────────────
ALTER TABLE holders
    ADD COLUMN IF NOT EXISTS anonymous BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE holders
    ADD COLUMN IF NOT EXISTS properties JSON DEFAULT '{}'::json;

UPDATE holders
    SET properties = '{}'::json
    WHERE properties IS NULL;

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
-- ────────────────────────────────────────────────────────────
ALTER TABLE edc_sts_client
    ADD COLUMN IF NOT EXISTS participant_context_id VARCHAR;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'participant_context'
    ) THEN
        UPDATE edc_sts_client AS sc
        SET participant_context_id = pc.participant_context_id
        FROM participant_context AS pc
        WHERE sc.participant_context_id IS NULL
          AND pc.did = sc.did;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM edc_sts_client
        WHERE participant_context_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Unable to backfill edc_sts_client.participant_context_id from participant_context.did';
    END IF;
END $$;

ALTER TABLE edc_sts_client
    ALTER COLUMN participant_context_id SET NOT NULL;

-- NOTE: private_key_alias and public_key_reference are no longer used in v0.15
-- but kept as-is since they cause no issues. Will be cleaned up later if needed.

-- ============================================================
-- keypair_resource: add usage column
-- v0.15 requires a usage column (JSON array of KeyPairUsage values).
-- Existing v0.14 keypairs get all usages assigned.
-- ============================================================
ALTER TABLE keypair_resource
    ADD COLUMN IF NOT EXISTS usage VARCHAR;

UPDATE keypair_resource
    SET usage = '["sign_credentials","sign_presentation","sign_token"]'
    WHERE usage IS NULL;

ALTER TABLE keypair_resource
    ALTER COLUMN usage SET NOT NULL;

COMMIT;
