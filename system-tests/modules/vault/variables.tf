variable "participant_name" {}

variable "tls_enabled" {
  description = "Enable TLS (HTTPS) for vault. When false, vault runs with plain HTTP."
  type        = bool
  default     = true
}

variable "internal_tls_secret_name" {
  description = "Name of the Kubernetes secret containing the shared internal TLS certificate (tls.crt, tls.key, ca.crt) used as vault's server certificate."
  type        = string
  default     = "internal-service-tls"
}

variable "ingress_tls_secret_name" {
  description = "Name of the Kubernetes secret (type kubernetes.io/tls) used by the nginx ingress controller for TLS termination on the vault ingress."
  type        = string
  default     = "internal-service-tls"
}

variable "ingress_proxy_ssl_ca_secret_name" {
  description = "Name of the Kubernetes secret containing the internal CA certificate (ca.crt) used by nginx-ingress proxy-ssl-secret to verify upstream backend TLS."
  type        = string
  default     = "nginx-proxy-ssl-ca"
}