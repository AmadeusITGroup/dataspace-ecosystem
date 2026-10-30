# Upgrade to EDC 0.18.1 (DSE 0.7.x → this release)

DSE moves from EDC Connector 0.16.1 / IdentityHub 0.16.0 to **EDC 0.18.1** (Connector, IdentityHub, IssuerService, FederatedCatalog) and Technology-Azure 0.18.0. Tracking ticket: PSMP-5859 (implementation); impact study: PSMP-5858.

## Upgrade order
1. **Back up** the participant databases (consumer / provider), the authority database and the vault secret `<participant>-aes` of every IdentityHub, IssuerService and telemetry-agent.
2. **Run the SQL migration while 0.7.x is still running.** The scripts are additive and idempotent, and 0.7.x keeps working on the migrated schema:
   ```bash
   ./apply-migrations.sh --host <db-host> --port <port> --user <superuser> --pass <password>
   # participant-migration.sql -> consumerdb, providerdb ; authority-migration.sql -> authoritydb
   ```
3. **Roll out the new images together with the new charts and configuration.** Swapping images alone is not an upgrade (see "Configuration changes").
4. Verify the following:
   - no `SEVERE` / `EdcException` in the control plane, data plane, IdentityHub and IssuerService logs;
   - existing agreements and transfers are readable;
   - a new negotiation reaches `FINALIZED`.

The data-plane tables `edc_accesstokendata` and `edc_data_plane` are **not** in the SQL scripts on purpose. The data plane creates them at its first start (`EDC_SQL_SCHEMA_AUTOCREATE=true`, the chart default), owned by the participant DB user. If you create them as a superuser instead, the data plane fails at boot with `must be owner of relation edc_accesstokendata`. If autocreate is disabled, create them **as the participant user**, using the upstream 0.18.1 files `accesstoken-data-schema.sql` and `dataplane-schema.sql`.

The upstream CEL store and task store SQL modules (`cel-expression-schema`, `edc_tasks`) are not on any DSE runtime classpath, so those schema changes do not apply here. The IdentityHub `credentials-schema.sql` is unchanged between 0.16 and 0.18.1 apart from a comment.

## Rollback
Redeploy the 0.7.x images **and** the 0.7.x charts / configuration. No DB step is needed: the new columns and tables are ignored by 0.7.x (`rollback.sql` is documentation only). The new configuration below cannot be used with the old images, because 0.16.1 only reads the `edc.iam.trusted-issuer` prefix.

## Configuration changes (handled by the charts / Terraform in this release)
| Change | Why |
|---|---|
| `EDC_PARTICIPANTS_CONFIG_ENCRYPTION_ALGORITHM=aes` on IdentityHub, IssuerService and telemetry-agent only | 0.18 has no default algorithm any more, and IH/IS now persist participant-context config. Other runtimes do not ship `aes` and fail if it is set. |
| `edc.policy.monitor.state-machine.*` → `edc.policy.monitor.period` (ISO-8601, e.g. `PT5S`) | the policy monitor is now a watchdog; the control plane refuses to boot with the old keys |
| `edc.iam.trusted-issuer.*` → `edc.iam.trustedissuer.*` | prefix renamed in 0.17 (the old one still works but is deprecated) |
| `EDC_IAM_ISSUER_ID` removed | deprecated in 0.17; the DID now defaults to `edc.participant.id`, which has the same value |
| IssuerService endpoint in the authority DID document: `/v1beta/participants/<base64 DID>` | Identity / Issuer-admin APIs moved from `/v1alpha` to `/v1beta` |
| Launchers run with `--no-color --log-level=INFO` | the EDC console monitor replaced the JDK logger |

## Behaviour changes for operators and API consumers
- **AES key is now persistent data.** `<participant>-aes` encrypts `edc_participant_context_config.private_entries`. Back it up, and never rotate it without re-encrypting. The devbox keygen job now writes it only if it is absent.
- **Log format changed**: lines now look like `INFO 2026-10-14T07:40:41.46… message` / `WARNING …` / `SEVERE …`. Splunk queries and alerts based on the old JDK-logger format must be updated.
- **Identity / Issuer-admin APIs** are now served on `/v1beta/participants/<base64 DID>/…`. Old `/v1alpha/participants/<base64 DID>/…` URLs and the `/v1alpha/participants` collection URL (create / list participants) keep working: they are rewritten by the `legacy-participant-id-path` extension. A raw `%`-encoded DID in the path is rejected by Jetty, so keep using base64.
- **Federated catalog query** `POST /api/catalog/v1alpha/catalog/query` (port 8383) is unchanged. It is now served by DSE, so DID documents need no update.
- **Management API v3** is unchanged, with the same JSON-LD keys (`dcat:dataset`, `dcat:distribution`, `odrl:hasPolicy`, …). It is deprecated upstream in favour of v4.
- **Management API `/v4beta` is gone; use `/v4`** (same endpoints, stable). Clients configured for `/v4beta` (e.g. cle-subscription-registry local setup) get 404.
- **Contract requests: do not send `profile` together with `protocol`.** In EDC 0.18 a `profile` field in a `ContractRequest` (v3 and v4) replaces the `protocol` of the negotiation; a non-protocol value (e.g. `https://w3id.org/idsa/DP`) makes the negotiation end `TERMINATED` ("No callback address found for protocol"). Send only `protocol: dataspace-protocol-http:2025-1`.
- **The hashicorp data plane now persists access tokens and data flows**, so in-flight PULL transfers survive data-plane restarts. Tokens issued by 0.7.x were kept in memory only, so **PULL transfers started on 0.7.x must be restarted once** after the cut-over: terminate, then start a new transfer.
- **Suspend → resume of a transfer does not work on 0.18.1**: the transfer stays `RESUMED` and the provider terminates it. Upstream rewrote the resume protocol ([eclipse-edc/Connector#5709](https://github.com/eclipse-edc/Connector/pull/5709)). Workaround: terminate the transfer and start a new one. Terminate works.
- New columns `claims` (agreement and transfer process) and `data_address_owner` (transfer process) are filled for new records only.

## Known non-blocking warnings
- legacy data plane deprecated: the data-plane stack is removed in EDC 1.0;
- Management API v3 deprecated;
- `data-plane-public-api-v2` deprecated.
