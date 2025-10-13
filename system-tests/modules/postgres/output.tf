output "postgres_server_fqdn" {
  value = kubernetes_service.db-service.metadata[0].name
}

output "postgres_admin_credentials_secret_name" {
  value = kubernetes_secret.db-admin-credentials.metadata.0.name
}