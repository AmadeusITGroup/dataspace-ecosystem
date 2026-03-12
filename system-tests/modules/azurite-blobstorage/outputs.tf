output "azurite_blob_endpoint" {
  description = "The internal Kubernetes blob endpoint for Azurite"
  value       = "http://azurite-blobstorage:10000"
}

output "azurite_account_name" {
  description = "The Azurite storage account name"
  value       = local.account_name
}

output "azurite_account_key" {
  description = "The Azurite storage account key"
  value       = local.account_key
  sensitive   = true
}

output "provider_container" {
  description = "The provider source container name"
  value       = "provider-src"
}

output "consumer_container" {
  description = "The consumer destination container name"
  value       = "consumer-dest"
}
