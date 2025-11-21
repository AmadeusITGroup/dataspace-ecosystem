variable "secret_name" {
  description = "Name of the Kubernetes secret to create"
  type        = string
}

variable "namespace" {
  description = "Kubernetes namespace for the secret"
  type        = string
  default     = "default"
}

variable "common_name" {
  description = "Common name for the TLS certificate"
  type        = string
  default     = "kafka-proxy-listener"
}

variable "dns_names" {
  description = "List of DNS names for the certificate"
  type        = list(string)
  default = [
    "localhost",
    "*.default.svc.cluster.local",
    "*.kafka-proxy",
  ]
}

variable "ip_addresses" {
  description = "List of IP addresses for the certificate"
  type        = list(string)
  default = [
    "127.0.0.1",
  ]
}
