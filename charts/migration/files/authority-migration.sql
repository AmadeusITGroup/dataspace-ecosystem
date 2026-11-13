-- ============================================================
-- EDC v0.16.1 → v0.18.1: Authority database migration
-- Target database: authoritydb  (IdentityHub + Issuer Service + federated catalog tables)
--
-- Tables modified:
--   credential_definitions — add additional_context (IdentityHub #941). Read unconditionally by
--                            SqlCredentialDefinitionStore at 0.18.1 (fromJson(additional_context)).
--
-- Verified scope: the only IdentityHub *.sql changes between v0.16.0 and v0.18.1 are this column
-- and a comment in credentials-schema.sql. participant_context is unchanged. Stored credential
-- formats ("VC1_0_JWT") are still accepted (CredentialProfile.formatForProfile falls back to the
-- enum name), so no data migration is needed for IdentityHub #993.
--
-- MIGRATION STRATEGY: additive only. The DEFAULT backfills existing rows, so the NOT NULL
-- constraint (as declared by EDC) is satisfied immediately. EDC 0.16.x ignores the column.
--
-- Idempotent: safe to re-run. Run BEFORE starting the v0.18.1 binaries.
-- ============================================================
BEGIN;

ALTER TABLE IF EXISTS credential_definitions
    ADD COLUMN IF NOT EXISTS additional_context JSON NOT NULL DEFAULT '[]';

COMMIT;
