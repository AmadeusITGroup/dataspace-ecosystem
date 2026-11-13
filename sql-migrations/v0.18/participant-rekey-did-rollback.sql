-- ============================================================
-- ROLLBACK of participant-rekey-did.sql: key the control-plane data of ONE participant by its old DID again
-- Target database: one participant database (consumerdb, providerdb, ...), run once per participant,
-- with the same old_did / new_did as participant-rekey-did.sql.
--
-- When: before deploying the 0.7.x images and charts again (UPGRADE.md, Rollback). The 0.7.x runtime
-- has the DID with port and serves only the rows keyed by it. Fallback: restore the participant
-- database backup of "Upgrade order" step 1 instead (it loses what was created since).
--
-- Usage:
--   psql -X -v ON_ERROR_STOP=1 -h <host> -p <port> -U <user> -d <participant db> \
--        -v old_did='did:web:<p>-identityhub%3A8383:api:did' \
--        -v new_did='did:web:<p>-identityhub:api:did' \
--        -f participant-rekey-did-rollback.sql
--
-- The exact reverse of participant-rekey-did.sql, on the same columns: every value equal to new_did
-- becomes old_did. That includes the rows created on 0.18 under the new DID (offers, negotiations,
-- agreements, transfer processes, EDR entries): they stay listed on 0.7.x, but agreements concluded
-- with the new DIDs cannot be used there (UPGRADE.md, Rollback).
--
-- Safety: one transaction. It stops before changing anything when old_did or new_did is missing or
-- empty, both are equal, a value does not start with did:, new_did contains %3A, or the
-- participant_context table of this database lacks one of the two DIDs (wrong DID or wrong database).
-- Idempotent: a second run changes 0 rows. It prints, per column, the rows keyed by old_did and by
-- new_did before and after.
-- ============================================================
\set ON_ERROR_STOP on

\if :{?old_did}
\else
\echo 'participant-rekey-did-rollback.sql: psql variable old_did is missing: -v old_did=<DID with port> -v new_did=<DID without port>'
DO $$ BEGIN RAISE EXCEPTION 'psql variable old_did is required'; END $$;
\endif
\if :{?new_did}
\else
\echo 'participant-rekey-did-rollback.sql: psql variable new_did is missing: -v old_did=<DID with port> -v new_did=<DID without port>'
DO $$ BEGIN RAISE EXCEPTION 'psql variable new_did is required'; END $$;
\endif

BEGIN;

-- do not wait forever for a row lock held by a running runtime: fail, nothing is changed, run again
SET LOCAL lock_timeout = '60s';
SET LOCAL dse.rekey_old_did = :'old_did';
SET LOCAL dse.rekey_new_did = :'new_did';

SELECT current_database() AS database, :'new_did' AS from_new_did, :'old_did' AS back_to_old_did;

CREATE TEMP TABLE dse_rekey_report
(
    step                 INT,
    "table.column"       TEXT,
    "old_did rows before" BIGINT,
    "new_did rows before" BIGINT,
    "re-keyed"           BIGINT,
    "old_did rows after"  BIGINT,
    "new_did rows after"  BIGINT
) ON COMMIT DROP;

DO $rekey$
DECLARE
    v_old     TEXT := current_setting('dse.rekey_old_did');
    v_new     TEXT := current_setting('dse.rekey_new_did');
    -- keep this list identical to participant-rekey-did.sql
    v_columns TEXT[] := ARRAY [
        'edc_asset.participant_context_id',
        'edc_policydefinitions.participant_context_id',
        'edc_contract_definitions.participant_context_id',
        'edc_contract_negotiation.participant_context_id',
        'edc_contract_agreement.agr_participant_context_id',
        'edc_transfer_process.participant_context_id',
        'edc_edr_entry.participant_context_id'
        ];
    v_table   TEXT;
    v_column  TEXT;
    v_old_before BIGINT;
    v_new_before BIGINT;
    v_updated    BIGINT;
    v_old_after  BIGINT;
    v_new_after  BIGINT;
BEGIN
    IF coalesce(v_old, '') = '' OR coalesce(v_new, '') = '' THEN
        RAISE EXCEPTION 'old_did and new_did must not be empty';
    END IF;
    IF v_old = v_new THEN
        RAISE EXCEPTION 'old_did and new_did are equal (%): nothing to re-key', v_old;
    END IF;
    IF v_old NOT LIKE 'did:%' OR v_new NOT LIKE 'did:%' THEN
        RAISE EXCEPTION 'old_did (%) and new_did (%) must be DIDs (did:...)', v_old, v_new;
    END IF;
    IF position('%3a' IN lower(v_new)) > 0 THEN
        RAISE EXCEPTION 'new_did (%) contains %%3A, i.e. a port: pass the same variables as for participant-rekey-did.sql', v_new;
    END IF;
    IF to_regclass('participant_context') IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM participant_context WHERE participant_context_id = v_old) THEN
            RAISE EXCEPTION 'old_did (%) is not a participant context of database %: check the DID and the database', v_old, current_database();
        END IF;
        IF NOT EXISTS (SELECT 1 FROM participant_context WHERE participant_context_id = v_new) THEN
            RAISE EXCEPTION 'new_did (%) is not a participant context of database %: check the DID and the database', v_new, current_database();
        END IF;
    END IF;

    FOR i IN 1 .. array_length(v_columns, 1)
        LOOP
            v_table := split_part(v_columns[i], '.', 1);
            v_column := split_part(v_columns[i], '.', 2);
            IF to_regclass(v_table) IS NULL THEN
                RAISE NOTICE 'table % does not exist in database %, skipped', v_table, current_database();
                INSERT INTO dse_rekey_report VALUES (i, v_columns[i] || ' (table absent)', NULL, NULL, 0, NULL, NULL);
                CONTINUE;
            END IF;
            EXECUTE format('SELECT count(*) FILTER (WHERE %1$I = $1), count(*) FILTER (WHERE %1$I = $2) FROM %2$I', v_column, v_table)
                INTO v_old_before, v_new_before USING v_old, v_new;
            EXECUTE format('UPDATE %1$I SET %2$I = $1 WHERE %2$I = $2', v_table, v_column) USING v_old, v_new;
            GET DIAGNOSTICS v_updated = ROW_COUNT;
            EXECUTE format('SELECT count(*) FILTER (WHERE %1$I = $1), count(*) FILTER (WHERE %1$I = $2) FROM %2$I', v_column, v_table)
                INTO v_old_after, v_new_after USING v_old, v_new;
            INSERT INTO dse_rekey_report VALUES (i, v_columns[i], v_old_before, v_new_before, v_updated, v_old_after, v_new_after);
        END LOOP;
END
$rekey$;

SELECT "table.column", "old_did rows before", "new_did rows before", "re-keyed", "old_did rows after", "new_did rows after"
FROM (SELECT * FROM dse_rekey_report
      UNION ALL
      SELECT 1000, 'total', sum("old_did rows before"), sum("new_did rows before"), sum("re-keyed"),
             sum("old_did rows after"), sum("new_did rows after")
      FROM dse_rekey_report) AS report
ORDER BY step;

COMMIT;
