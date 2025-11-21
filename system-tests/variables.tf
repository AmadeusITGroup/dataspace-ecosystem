variable "provider_name" {
  default = "provider"
}

variable "consumer_name" {
  default = "consumer"
}

variable "authority_name" {
  default = "authority"
}

variable "kube_context" {
  default = "kind-dse-cluster"
}

variable "kube_config_path" {
  default = "~/.kube/config"
}

variable "environment" {
  description = "The environment (local, devbox or production)"
  type        = string
  default     = "production" # Default to "production" if not defined
}

variable "account_secret_azurite" {
  default     = "pass"
  description = "Account secret for event hub"
  sensitive   = true
}

variable "account_name_azurite" {
  default     = "user"
  description = "Account name for event hub"
}

variable "devbox-registry" {
  description = "The container registry for devbox environment"
  type        = string
  default     = "cds.sec.io"
}

variable "devbox-registry-cred" {
  description = "The image pull secret name for devbox environment"
  type        = string
  default     = "cdsregistrycred"
}


# Authentication Configuration for System Tests
# This file provides variables for enabling authentication in system tests

variable "auth_enabled" {
  description = "Enable downstream authentication for consumer proxies"
  type        = bool
  default     = true
}

variable "auth_mechanism" {
  description = "Authentication mechanism: PLAIN (JWT-over-PLAIN) or OAUTHBEARER (proper OAuth2)"
  type        = string
  default     = "PLAIN"
  validation {
    condition     = contains(["PLAIN", "OAUTHBEARER"], var.auth_mechanism)
    error_message = "Authentication mechanism must be either PLAIN or OAUTHBEARER."
  }
}

variable "auth_tenant_id" {
  description = "Microsoft Entra ID (Azure AD) tenant ID for authentication"
  type        = string
  default     = "<tenant-id>"
  sensitive   = false
}

variable "auth_client_id" {
  description = "Application client ID registered in Entra ID for authentication"
  type        = string
  default     = "<client-id>"
  sensitive   = false
}

variable "auth_static_users" {
  description = "Static users for fallback authentication (format: username1:password1,username2:password2)"
  type        = string
  default     = "admin:admin-secret"
  sensitive   = true
}

# TLS Listener Configuration
variable "tls_listener_enabled" {
  description = "Enable TLS for proxy listener (clients connect to proxy via TLS)"
  type        = bool
  default     = false # Disable by default for testing
}

# Vault Configuration
variable "vault_folder" {
  description = "Vault folder for secrets organization. Empty = secret/, 'consumer' = secret/consumer/"
  type        = string
  default     = ""
}
