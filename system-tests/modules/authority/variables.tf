variable "authority_name" {}

variable "db_server_fqdn" {}

variable "postgres_admin_credentials_secret_name" {
  description = "(Required) Secret containing the DB Admin credentials"
}

variable "environment" {
  description = "The environment (local or production)"
  type        = string
}

variable "account_secret_azurite" {
  description = "Account secret for event hub"
  sensitive   = true
}

variable "account_name_azurite" {
  description = "Account name for event hub"
}

variable "devbox-registry" {
  description = "The container registry for devbox environment"
  type        = string
}

variable "devbox-registry-cred" {
  description = "The image pull secret name for devbox environment"
  type        = string
}

variable "dse_namespace_prefix" {
  description = "DSE namespace prefix"
  type        = string
  default     = "dse"
}

variable "tls_enabled" {
  description = "Enable TLS (HTTPS) for all authority components — both internal pod-to-pod TLS and ingress TLS termination."
  type        = bool
  default     = true
}

variable "internal_tls_secret_name" {
  description = "Name of the Kubernetes secret containing the shared internal TLS certificate (tls.crt, tls.key, ca.crt) used for pod-to-pod HTTPS."
  type        = string
  default     = "internal-service-tls"
}

variable "ingress_tls_secret_name" {
  description = "Name of the Kubernetes secret (type kubernetes.io/tls) used by the nginx ingress controller for TLS termination."
  type        = string
  default     = "internal-service-tls"
}

variable "ingress_proxy_ssl_ca_secret_name" {
  description = "Name of the Kubernetes secret containing the internal CA certificate (ca.crt) used by nginx-ingress proxy-ssl-secret to verify upstream backend TLS."
  type        = string
  default     = "nginx-proxy-ssl-ca"
}

variable "dse_policy_prefix" {
  description = "DSE policy prefix"
  type        = string
  default     = "dse-policy"
}
