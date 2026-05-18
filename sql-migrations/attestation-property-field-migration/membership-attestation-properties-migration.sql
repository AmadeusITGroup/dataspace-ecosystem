-- ============================================================================
-- Migration: Add extensible properties JSON column to membership_attestation
-- ============================================================================
-- Adds a properties JSON column for storing extensible attributes (e.g.,
-- companySegment). The json-database attestation source flattens JSON keys
-- into the VC mapping result, so no generated columns are needed.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'membership_attestation') THEN
        -- Add extensible properties JSON column
        ALTER TABLE membership_attestation
        ADD COLUMN IF NOT EXISTS properties JSON NOT NULL DEFAULT '{}'::json;

        -- Drop the generated column if it exists from a prior migration
        ALTER TABLE membership_attestation
        DROP COLUMN IF EXISTS company_segment;

        COMMENT ON COLUMN membership_attestation.properties IS 'Extensible key-value properties stored as JSON (e.g., companySegment, companySize)';
    END IF;
END $$;
