output "vault_url" {
  value = "http://${var.participant_name}-vault:${local.vault_port}"
}

output "vault_secret_name" {
  value = kubernetes_secret.vault-secret.metadata.0.name
}