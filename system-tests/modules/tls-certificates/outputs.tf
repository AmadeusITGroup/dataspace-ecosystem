output "secret_name" {
  description = "Name of the created Kubernetes secret"
  value       = kubernetes_secret.tls_listener.metadata[0].name
}

output "ca_cert_pem" {
  description = "CA certificate in PEM format"
  value       = tls_self_signed_cert.ca.cert_pem
  sensitive   = true
}

output "server_cert_pem" {
  description = "Server certificate in PEM format"
  value       = tls_locally_signed_cert.server.cert_pem
  sensitive   = true
}

output "server_key_pem" {
  description = "Server private key in PEM format"
  value       = tls_private_key.server.private_key_pem
  sensitive   = true
}
