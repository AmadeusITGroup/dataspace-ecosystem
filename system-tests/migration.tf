# EDC 0.7.x -> 0.18.1 migration, driven by Helm hooks. Modeled on the CLE argocd preinstall-hook
# pattern so the SAME chart runs under ArgoCD later (native PreSync/PostSync hooks) with no rewrite.
#
# Two separate releases enforce the strict order  schema -> runtime -> cut-over, instead of relying
# on hook phases inside one release (review r4523993586):
#
#   1. helm_release.migration_schema  (schemaMigration=true, didCutover=false)
#        NO depends_on -> Terraform may run it concurrently with / before the component upgrades.
#        The SQL is additive + idempotent (ADD COLUMN IF NOT EXISTS) and UPGRADE.md requires it to
#        run while 0.7.x is still up; the new columns are simply unused until the new binaries start.
#   2. component releases (module.participant / module.authority) roll the 0.18.1 runtimes.
#   3. helm_release.migration_cutover (schemaMigration=false, didCutover=true)
#        depends_on the component releases -> the DID re-key + credential reissue/cleanup run only
#        after the 0.18.1 runtimes are applied (and the rekey Job still does a bounded readiness
#        check that the new port-less DID exists before re-keying).
#
# Under ArgoCD the same order is expressed with sync-waves: schema Job in an early wave, the
# component Applications next, the cut-over Job in a later wave (pre/post hooks within each).

variable "run_migration" {
  description = "Run the 0.7.x -> 0.18.1 migration (schema release always; cut-over release after the runtimes are 0.18.1)."
  type        = bool
  default     = false
}

variable "migration_tools_image" {
  description = "Override the cut-over reissue Job tools image (local kind rehearsal uses a preloaded image)."
  type        = string
  default     = ""
}

variable "migration_tools_pull_policy" {
  description = "Override the cut-over reissue Job tools imagePullPolicy (Never for a preloaded rehearsal image)."
  type        = string
  default     = ""
}

# Per-role catalog visibility of each participant's own catalog, matching the baseline onboarding
# (modules/authority/insert_rows.sh registers provider+consumer; consumer's catalog is visible).
# Keyed by ROLE, not by the literal name, so renaming a participant keeps the right visibility.
variable "migration_visibility" {
  description = "Catalog visibility per role (provider/consumer/authority) for the migration reissue step."
  type        = map(bool)
  default = {
    provider  = false
    consumer  = true
    authority = false
  }
}

# Deployment-derived migration values. Everything the chart would otherwise hard-code to the
# default provider/consumer/authority names is derived here from the SAME name variables the
# component modules use (modules/participant main.tf: db_name = "<name>db"; identityhub.tf:
# did:web:<name>-identityhub:api:did). This keeps the migration correct when the deployment
# renames participants (e.g. provider_name=supplier -> supplierdb) and portable to ArgoCD,
# where these names come from the environment's values instead of the chart defaults.
locals {
  # new (0.18.x, port-less) and old (0.7.x, %3A8383) DID for a given participant name
  mig_new_did = { for n in [var.provider_name, var.consumer_name, var.authority_name] : n => "did:web:${n}-identityhub:api:did" }
  mig_old_did = { for n in [var.provider_name, var.consumer_name, var.authority_name] : n => "did:web:${n}-identityhub%3A8383:api:did" }

  # shared, deployment-derived override applied to BOTH migration releases
  migration_values = {
    db = {
      participantDbs = ["${var.provider_name}db", "${var.consumer_name}db"]
      authorityDb    = "${var.authority_name}db"
    }

    # one re-key pair per participant database (provider + consumer)
    rekey = [
      { db = "${var.provider_name}db", oldDid = local.mig_old_did[var.provider_name], newDid = local.mig_new_did[var.provider_name] },
      { db = "${var.consumer_name}db", oldDid = local.mig_old_did[var.consumer_name], newDid = local.mig_new_did[var.consumer_name] },
    ]

    reissue = {
      scheme            = var.tls_enabled ? "https" : "http"
      issuerServiceHost = "${var.authority_name}-issuerservice"
      authorityNewDid   = local.mig_new_did[var.authority_name]
      # issueradmin path needs the old DID double-encoded (%3A -> %253A)
      authorityOldDidEnc         = "did:web:${var.authority_name}-identityhub%253A8383:api:did"
      federatedCatalogDeployment = "${var.authority_name}-federatedcatalog"
      participants = [
        {
          name            = var.authority_name
          newDid          = local.mig_new_did[var.authority_name]
          identityHubHost = "${var.authority_name}-identityhub"
          domain          = "route"
          visibility      = lookup(var.migration_visibility, "authority", false)
        },
        {
          name            = var.provider_name
          newDid          = local.mig_new_did[var.provider_name]
          identityHubHost = "${var.provider_name}-identityhub"
          domain          = "route"
          visibility      = lookup(var.migration_visibility, "provider", false)
        },
        {
          name            = var.consumer_name
          newDid          = local.mig_new_did[var.consumer_name]
          identityHubHost = "${var.consumer_name}-identityhub"
          domain          = "route"
          visibility      = lookup(var.migration_visibility, "consumer", true)
        },
      ]
      # port-ful (0.7.x) identities to delete on cleanup — explicit allowlist, never pattern-matched
      oldHolders = [
        local.mig_old_did[var.provider_name],
        local.mig_old_did[var.consumer_name],
        local.mig_old_did[var.authority_name],
      ]
    }
  }
}

# Stage 1 — schema migration. No depends_on: runs before/with the runtime upgrade.
resource "helm_release" "migration_schema" {
  count = var.run_migration ? 1 : 0

  name      = "mig-schema"
  namespace = "default"
  chart     = "${path.module}/../charts/migration"

  wait    = true
  timeout = 600

  # Deployment-derived names/DIDs/hosts override the chart defaults (see local.migration_values).
  values = [yamlencode(local.migration_values)]

  set = [
    { name = "enabled", value = "true" },
    { name = "schemaMigration", value = "true" },
    { name = "didCutover", value = "false" },
  ]
}

# Stage 3 — DID cut-over. depends_on the runtimes so it never runs against 0.7.x.
resource "helm_release" "migration_cutover" {
  count = var.run_migration ? 1 : 0

  name      = "mig-cutover"
  namespace = "default"
  chart     = "${path.module}/../charts/migration"

  depends_on = [module.participant, module.authority, helm_release.migration_schema]

  wait          = true
  timeout       = 900
  recreate_pods = false

  # Deployment-derived names/DIDs/hosts override the chart defaults (see local.migration_values).
  values = [yamlencode(local.migration_values)]

  set = concat(
    [
      { name = "enabled", value = "true" },
      { name = "schemaMigration", value = "false" },
      { name = "didCutover", value = "true" },
    ],
    var.migration_tools_image != "" ? [{ name = "images.tools", value = var.migration_tools_image }] : [],
    var.migration_tools_pull_policy != "" ? [{ name = "images.toolsPullPolicy", value = var.migration_tools_pull_policy }] : [],
  )
}
