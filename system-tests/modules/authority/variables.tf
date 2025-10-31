variable "authority_name" {}

variable "db_server_fqdn" {}

variable "postgres_admin_credentials_secret_name" {
  description = "(Required) Secret containing the DB Admin credentials"
}

variable "environment" {
  description = "The environment (local or production)"
  type        = string
}

variable "devbox-registry" {
  description = "The container registry for devbox environment"
  type        = string
}

variable "devbox-registry-cred" {
  description = "The image pull secret name for devbox environment"
  type        = string
}
