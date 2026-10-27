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
  default     = "production"
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

variable "azurite_blobstorage_account_name" {
  default     = "devstoreaccount1"
  description = "Azurite blob storage account name (well-known default)"
}

variable "azurite_blobstorage_account_key" {
  description = "Azurite blob storage account key (well-known default)"
  default     = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==" # gitleaks:allow
  sensitive   = true
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

# Global TLS Configuration
variable "tls_enabled" {
  description = "Enable TLS (HTTPS) for all components — both internal pod-to-pod TLS and ingress TLS termination. Set to false to use plain HTTP throughout."
  type        = bool
  default     = true
}

# Vault Configuration
variable "vault_folder" {
  description = "Vault folder for secrets organization. Empty = secret/, 'consumer' = secret/consumer/"
  type        = string
  default     = ""
}

# Category whitelist
variable "category_whitelist_enabled" {
  description = "Render the shared category-whitelist ConfigMap and mount it into the control plane through the chart's generic hooks. When false there is no ConfigMap and no mount. The control plane reads the file once at boot and system-tests runs no ConfigMap reloader, so after changing the file content re-apply and restart the control-plane deployments (kubectl rollout restart)."
  type        = bool
  default     = true
}

variable "category_whitelist_file" {
  description = "Path to the category whitelist JSON loaded into the ConfigMap. Empty (the default) uses the committed example list (category-whitelist.example.json)."
  type        = string
  default     = ""
}

# Charts path (relative to system-tests). Defaults to the repo's charts/.
variable "charts_path" {
  description = "Path (relative to system-tests) to the directory holding the chart deployed for each participant's control plane."
  type        = string
  default     = "../charts"
}

# Control-plane chart selection (generic). Defaults deploy the local
# control-plane chart directly; a caller can swap in an umbrella and nest the
# control-plane values under a subchart key.
variable "controlplane_chart" {
  description = "Name of the chart (under the control-plane chart directory) to deploy for each participant's control plane."
  type        = string
  default     = "control-plane"
}

variable "controlplane_chart_repository" {
  description = "Directory holding the control-plane chart. Empty falls back to charts_path, so only the control plane can be served from a different directory than the other charts."
  type        = string
  default     = ""
}

variable "controlplane_values_key" {
  description = "When set, the deployed chart is treated as an umbrella and the control-plane values are nested under this subchart key. Empty means the control-plane chart is deployed directly."
  type        = string
  default     = ""
}
