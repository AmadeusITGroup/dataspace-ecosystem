variable "azurite_version" {
  description = "Version of Azurite image to use"
  default     = "3.35.0"
}

variable "azure_cli_version" {
  description = "Version of Azure CLI image to use"
  default     = "2.84.0"
}

variable "account_name" {
  description = "Azurite storage account name"
  type        = string
  default     = "devstoreaccount1"
}

variable "account_key" {
  description = "Azurite storage account key"
  type        = string
  sensitive   = true
}
