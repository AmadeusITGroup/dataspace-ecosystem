-- ============================================================
-- DIDs without port: re-key the control-plane data of ONE participant to its new DID
-- Target database: one participant database (consumerdb, providerdb, ...), run once per participant
-- with that participant's own DIDs. Not for the authority database.
--
-- Why: DSE 0.7.x (EDC 0.16.1) keyed every control-plane row by the participant id of the runtime,
-- the DID with port (did:web:<p>-identityhub%3A8383:api:did). The 0.18.1 runtime has the DID
-- without port and serves only the participant context of its own DID: the 0.7.x offers are not in
-- its DSP catalog (0 datasets, dataset request 404) and the protocol services do not find the 0.7.x
-- negotiations, agreements and transfer processes. This script moves these rows to the new DID.
--
-- Usage (after the 0.18 runtimes are deployed, see UPGRADE.md, re-onboarding step 1b):
--   psql -X -v ON_ERROR_STOP=1 -h <host> -p <port> -U <user> -d <participant db> \
--        -v old_did='did:web:<p>-identityhub%3A8383:api:did' \
--        -v new_did='did:web:<p>-identityhub:api:did' \
--        -f participant-rekey-did.sql
--
-- Re-keyed columns (only where the value equals old_did exactly; nothing else in the rows changes):
--   edc_asset.participant_context_id                  the DSP catalog selects assets by participant context
--   edc_policydefinitions.participant_context_id      the policies of the offers, owned by the participant context
--   edc_contract_definitions.participant_context_id   the catalog and the contract request select them by participant context
--   edc_contract_negotiation.participant_context_id   protocol messages on another participant context's negotiation are refused
--   edc_contract_agreement.agr_participant_context_id a transfer request finds its agreement by participant context
--   edc_transfer_process.participant_context_id       protocol messages, data-plane selection and vault lookups use it
--   edc_edr_entry.participant_context_id              the consumer EDR entries of the transfer processes
--
-- Not changed:
--   * IdentityHub tables (participant_context, did_resources, keypair_resource, credential_resource,
--     edc_sts_client, edc_holder_credentialrequest, edc_credential_offers, edc_participant_context_config):
--     the 0.18 IdentityHub creates the participant context of the new DID, re-onboarding issues its credentials;
--   * counterparty ids and policy content (edc_contract_negotiation.counterparty_id / contract_offers,
--     edc_contract_agreement.consumer_agent_id / provider_agent_id / policy, edc_edr_entry.provider_id):
--     an agreement keeps the identities that concluded it (UPGRADE.md, re-onboarding step 4);
--   * edc_data_plane_instance: the control plane sets its participant context when the data plane
--     registers (at every data-plane start); edc_data_plane / edc_accesstokendata: no participant
--     context column, 0.7.x kept flows and tokens in memory;
--   * edc_policy_monitor, edc_agreement_retirement, edc_telemetry_record, edc_lease, edc_jti_validation:
--     no participant context column.
--
-- Safety: one transaction. It stops before changing anything when old_did or new_did is missing or
-- empty, both are equal, a value does not start with did:, new_did contains %3A (a port), or the
-- participant_context table of this database lacks one of the two DIDs (the 0.18 IdentityHub creates
-- the new one at its first start; otherwise wrong DID or wrong database). Idempotent: a second run
-- changes 0 rows. It prints, per column, the rows keyed by old_did and by new_did before and after.
-- Rollback: participant-rekey-did-rollback.sql with the same variables, or the participant DB backup.
-- ============================================================
\set ON_ERROR_STOP on

\if :{?old_did}
\else
\echo 'participant-rekey-did.sql: psql variable old_did is missing: -v old_did=<DID with port> -v new_did=<DID without port>'
DO $$ BEGIN RAISE EXCEPTION 'psql variable old_did is required'; END $$;
\endif
\if :{?new_did}
\else
\echo 'participant-rekey-did.sql: psql variable new_did is missing: -v old_did=<DID with port> -v new_did=<DID without port>'
DO $$ BEGIN RAISE EXCEPTION 'psql variable new_did is required'; END $$;
\endif

BEGIN;

-- do not wait forever for a row lock held by a running runtime: fail, nothing is changed, run again
SET LOCAL lock_timeout = '60s';
SET LOCAL dse.rekey_old_did = :'old_did';
SET LOCAL dse.rekey_new_did = :'new_did';

SELECT current_database() AS database, :'old_did' AS old_did, :'new_did' AS new_did;

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
    -- keep this list identical to participant-rekey-did-rollback.sql
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
        RAISE EXCEPTION 'new_did (%) contains %%3A, i.e. a port: pass the new DID without port', v_new;
    END IF;
    IF to_regclass('participant_context') IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM participant_context WHERE participant_context_id = v_new) THEN
            RAISE EXCEPTION 'new_did (%) is not a participant context of database %: deploy the 0.18 runtimes first (the IdentityHub creates it at startup), check the DID and the database', v_new, current_database();
        END IF;
        IF NOT EXISTS (SELECT 1 FROM participant_context WHERE participant_context_id = v_old) THEN
            RAISE EXCEPTION 'old_did (%) is not a participant context of database %: check the DID and the database', v_old, current_database();
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
            EXECUTE format('UPDATE %1$I SET %2$I = $2 WHERE %2$I = $1', v_table, v_column) USING v_old, v_new;
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
