variable "authority_name" {}

variable "db_server_fqdn" {}

variable "postgres_admin_credentials_secret_name" {
  description = "(Required) Secret containing the DB Admin credentials"
}

variable "environment" {
  description = "The environment (local or production)"
  type        = string
}