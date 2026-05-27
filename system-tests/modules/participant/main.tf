locals {
  privatekey_alias = var.participant_name
  publickey_alias  = "${local.privatekey_alias}-pub"

  db_name          = "${var.participant_name}db"
  db_user          = var.participant_name
  db_user_password = "${var.participant_name}pwd"

  authority_did = var.selfhosted_authority_did != "" ? var.selfhosted_authority_did : replace(local.did_url, var.participant_name, "authority")

  image_pull_policy = var.environment == "local" || var.environment == "selfhosted" ? "Never" : var.environment == "devbox" ? "Always" : "IfNotPresent"

  # Shared TLS helpers used across all participant component files
  scheme        = var.tls_enabled ? "https" : "http"
  tls_java_opts = var.tls_enabled ? "-Djavax.net.ssl.trustStore=/shared/cacerts -Djavax.net.ssl.trustStorePassword=changeit" : ""
  tls_config_props = var.tls_enabled ? join("\n", [
    "edc.web.https.keystore.path=/shared/keystore.p12",
    "edc.web.https.keystore.type=PKCS12",
    "edc.web.https.keystore.password=changeit",
    "edc.web.https.keymanager.password=changeit",
  ]) : ""
}

module "db" {
  source = "../db"

  db_name                                = local.db_name
  db_server_fqdn                         = var.db_server_fqdn
  db_user                                = local.db_user
  db_user_password                       = local.db_user_password
  postgres_admin_credentials_secret_name = var.postgres_admin_credentials_secret_name
}

resource "kubernetes_secret" "db-user-credentials" {

  metadata {
    name = "${local.db_user}-db-credentials"
  }

  data = {
    "username" = local.db_user
    "password" = local.db_user_password
  }
}


module "vault" {
  source = "../vault"

  participant_name                 = var.participant_name
  tls_enabled                      = var.tls_enabled
  internal_tls_secret_name         = var.internal_tls_secret_name
  ingress_tls_secret_name          = var.ingress_tls_secret_name
  ingress_proxy_ssl_ca_secret_name = var.ingress_proxy_ssl_ca_secret_name
}