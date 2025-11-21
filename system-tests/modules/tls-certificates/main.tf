# TLS Certificate Generation Module for Kafka Proxy Listener
# This module generates self-signed certificates for testing purposes

terraform {
  required_providers {
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}

# Generate CA private key
resource "tls_private_key" "ca" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

# Generate CA certificate
resource "tls_self_signed_cert" "ca" {
  private_key_pem = tls_private_key.ca.private_key_pem

  subject {
    common_name  = "Kafka Proxy CA"
    organization = "DXP Team"
    country      = "US"
  }

  validity_period_hours = 8760 # 1 year
  is_ca_certificate     = true

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "cert_signing",
    "crl_signing",
  ]
}

# Generate server private key
resource "tls_private_key" "server" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

# Generate certificate signing request for server
resource "tls_cert_request" "server" {
  private_key_pem = tls_private_key.server.private_key_pem

  subject {
    common_name  = var.common_name
    organization = "DXP Team"
    country      = "US"
  }

  dns_names    = var.dns_names
  ip_addresses = var.ip_addresses
}

# Sign server certificate with CA
resource "tls_locally_signed_cert" "server" {
  cert_request_pem   = tls_cert_request.server.cert_request_pem
  ca_private_key_pem = tls_private_key.ca.private_key_pem
  ca_cert_pem        = tls_self_signed_cert.ca.cert_pem

  validity_period_hours = 8760 # 1 year

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
    "client_auth",
  ]
}

# Create Kubernetes secret with TLS certificates
resource "kubernetes_secret" "tls_listener" {
  metadata {
    name      = var.secret_name
    namespace = var.namespace
    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app.kubernetes.io/component"  = "kafka-proxy-tls"
    }
  }

  type = "Opaque"

  data = {
    "tls.crt" = tls_locally_signed_cert.server.cert_pem
    "tls.key" = tls_private_key.server.private_key_pem
    "ca.crt"  = tls_self_signed_cert.ca.cert_pem
  }
}
