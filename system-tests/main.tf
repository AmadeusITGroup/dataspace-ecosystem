locals {
  participants = [var.provider_name, var.consumer_name]
}

###################################
## INTERNAL SERVICE TLS / HTTPS  ##
###################################

# Shared CA that signs all internal pod-to-pod TLS certificates.
resource "tls_private_key" "internal_ca" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "tls_self_signed_cert" "internal_ca" {
  private_key_pem = tls_private_key.internal_ca.private_key_pem

  subject {
    common_name  = "DXP Internal Service CA"
    organization = "DXP Team"
  }

  validity_period_hours = 8760 # 1 year
  is_ca_certificate     = true

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "cert_signing",
    "crl_signing",
  ]
}

# Wildcard certificate covering all ClusterIP service DNS names in the default namespace.
resource "tls_private_key" "internal_service" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_cert_request" "internal_service" {
  private_key_pem = tls_private_key.internal_service.private_key_pem

  subject {
    common_name  = "*.default.svc.cluster.local"
    organization = "DXP Team"
  }

  dns_names = [
    "*.default.svc.cluster.local",
    "*.default",
    "localhost",
    # Authority bare hostnames (used by DID resolution and internal HTTP calls)
    "${var.authority_name}-identityhub",
    "${var.authority_name}-issuerservice",
    "${var.authority_name}-federatedcatalog",
    "${var.authority_name}-federatedcatalogfilter",
    "${var.authority_name}-telemetryservice",
    "${var.authority_name}-telemetrycsvmanager",
    "${var.authority_name}-telemetrystorage",
    # Consumer participant bare hostnames
    "${var.consumer_name}-identityhub",
    "${var.consumer_name}-controlplane",
    "${var.consumer_name}-dataplane",
    "${var.consumer_name}-telemetryagent",
    # Provider participant bare hostnames
    "${var.provider_name}-identityhub",
    "${var.provider_name}-telemetryagent",
    "${var.provider_name}-controlplane",
    "${var.provider_name}-dataplane",
    # Vault bare hostnames (used by init job and nginx proxy)
    "${var.authority_name}-vault",
    "${var.consumer_name}-vault",
    "${var.provider_name}-vault",
  ]
}

resource "tls_locally_signed_cert" "internal_service" {
  cert_request_pem   = tls_cert_request.internal_service.cert_request_pem
  ca_private_key_pem = tls_private_key.internal_ca.private_key_pem
  ca_cert_pem        = tls_self_signed_cert.internal_ca.cert_pem

  validity_period_hours = 8760 # 1 year

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

resource "kubernetes_secret" "internal_service_tls" {
  metadata {
    name      = "internal-service-tls"
    namespace = "default"
    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app.kubernetes.io/component"  = "internal-tls"
    }
  }

  type = "kubernetes.io/tls"

  data = {
    "tls.crt" = "${tls_locally_signed_cert.internal_service.cert_pem}"
    "tls.key" = tls_private_key.internal_service.private_key_pem
    "ca.crt"  = tls_self_signed_cert.internal_ca.cert_pem
  }
}

# Dedicated CA-only secret used by the nginx-ingress proxy-ssl-secret annotation
# to verify upstream (backend pod) TLS certificates. Contains only the internal CA
# certificate — no private key is exposed to the ingress controller.
resource "kubernetes_secret" "nginx_proxy_ssl_ca" {
  metadata {
    name      = "nginx-proxy-ssl-ca"
    namespace = "default"
    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app.kubernetes.io/component"  = "nginx-proxy-ssl-ca"
    }
  }

  data = {
    "ca.crt" = tls_self_signed_cert.internal_ca.cert_pem
  }
}

###########################
## TLS CERTIFICATES      ##
###########################

# Generate TLS certificates for consumer kafka-proxy listener
module "consumer_tls_certificates" {
  source = "./modules/tls-certificates"

  secret_name = "consumer-kafka-proxy-listener-tls"
  namespace   = "default"
  common_name = "consumer-kafka-proxy-listener"

  dns_names = [
    "localhost",
    "*.default.svc.cluster.local",
    "consumer-kafkaproxy-kafka-proxy",
    "consumer-kafkaproxy-kafka-proxy.default.svc.cluster.local",
  ]
}

# Generate TLS certificates for provider kafka-proxy listener
module "provider_tls_certificates" {
  source = "./modules/tls-certificates"

  secret_name = "provider-kafka-proxy-listener-tls"
  namespace   = "default"
  common_name = "provider-kafka-proxy-listener"

  dns_names = [
    "localhost",
    "*.default.svc.cluster.local",
    "provider-kafkaproxy-kafka-proxy",
    "provider-kafkaproxy-kafka-proxy.default.svc.cluster.local",
  ]
}

###################
## POSTGRESQL DB ##
###################

module "postgres" {
  source = "./modules/postgres"
}

######################
## PROVIDER BACKEND ##
######################

module "backend-provider" {
  source               = "./modules/backend-service"
  name                 = var.provider_name
  environment          = var.environment
  devbox-registry      = var.devbox-registry
  devbox-registry-cred = var.devbox-registry-cred
}

##################
## PARTICIPANTS ##
##################

module "participant" {
  source = "./modules/participant"

  for_each                               = { for p in local.participants : p => p }
  participant_name                       = each.key
  participant_with_prefix                = "/${each.key}"
  db_server_fqdn                         = module.postgres.postgres_server_fqdn
  postgres_admin_credentials_secret_name = module.postgres.postgres_admin_credentials_secret_name
  environment                            = var.environment
  devbox-registry                        = var.devbox-registry
  devbox-registry-cred                   = var.devbox-registry-cred

  # Authentication Configuration
  auth_enabled      = var.auth_enabled
  auth_mechanism    = var.auth_mechanism
  auth_tenant_id    = var.auth_tenant_id
  auth_client_id    = var.auth_client_id
  auth_static_users = var.auth_static_users

  # TLS Listener Configuration
  tls_listener_enabled     = var.tls_listener_enabled
  tls_listener_cert_secret = each.key == "consumer" ? module.consumer_tls_certificates.secret_name : module.provider_tls_certificates.secret_name
  tls_listener_key_secret  = each.key == "consumer" ? module.consumer_tls_certificates.secret_name : module.provider_tls_certificates.secret_name
  tls_listener_ca_secret   = "" # Empty string disables mutual TLS (client certificate verification)

  # Vault Configuration
  vault_folder = var.vault_folder

  # Charts Path (relative to system-tests)
  charts_path = "../charts"

  # Internal HTTPS
  internal_tls_secret_name         = kubernetes_secret.internal_service_tls.metadata[0].name
  ingress_proxy_ssl_ca_secret_name = kubernetes_secret.nginx_proxy_ssl_ca.metadata[0].name

  depends_on = [
    module.consumer_tls_certificates,
    module.provider_tls_certificates,
    kubernetes_secret.internal_service_tls,
    kubernetes_secret.nginx_proxy_ssl_ca
  ]
}
#
###############
## AUTHORITY ##
###############

module "authority" {
  source = "./modules/authority"

  authority_name                         = var.authority_name
  db_server_fqdn                         = module.postgres.postgres_server_fqdn
  postgres_admin_credentials_secret_name = module.postgres.postgres_admin_credentials_secret_name
  environment                            = var.environment
  account_name_azurite                   = var.account_name_azurite
  account_secret_azurite                 = var.account_secret_azurite
  devbox-registry                        = var.devbox-registry
  devbox-registry-cred                   = var.devbox-registry-cred

  # Internal HTTPS
  internal_tls_secret_name         = kubernetes_secret.internal_service_tls.metadata[0].name
  ingress_proxy_ssl_ca_secret_name = kubernetes_secret.nginx_proxy_ssl_ca.metadata[0].name
}

#####################
## KAFKA BROKER E2E ##
#####################

module "broker" {
  source               = "./modules/broker"
  environment          = var.environment
  devbox-registry      = var.devbox-registry
  devbox-registry-cred = var.devbox-registry-cred
}

############################
## AZURITE BLOB STORAGE E2E ##
############################

module "azurite_blobstorage" {
  source = "./modules/azurite-blobstorage"

  account_name = var.azurite_blobstorage_account_name
  account_key  = var.azurite_blobstorage_account_key
}

