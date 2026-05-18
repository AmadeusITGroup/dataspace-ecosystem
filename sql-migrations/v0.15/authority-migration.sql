-- ============================================================
-- EDC v0.14 → v0.15.1: Authority database migration
-- Target database: authoritydb
--
-- Tables modified:
--   credential_resource    — add vc_usage column
--   participant_context    — add state_timestamp column
--   credential_definitions — replace formats array with format column
--   edc_holder_credentialrequest — transform types_and_formats to ids_and_formats
-- ============================================================
BEGIN;

-- ────────────────────────────────────────────────────────────
-- credential_resource: add vc_usage column
-- ────────────────────────────────────────────────────────────
ALTER TABLE credential_resource
    ADD COLUMN IF NOT EXISTS vc_usage character varying;

UPDATE credential_resource
    SET vc_usage = 'Holder'
    WHERE vc_usage IS NULL;

ALTER TABLE credential_resource
    ALTER COLUMN vc_usage SET NOT NULL;

ALTER TABLE credential_resource
    ALTER COLUMN vc_usage SET DEFAULT 'Holder';

CREATE INDEX IF NOT EXISTS idx_credential_resource_vc_usage
    ON credential_resource (vc_usage);

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

COMMIT;
