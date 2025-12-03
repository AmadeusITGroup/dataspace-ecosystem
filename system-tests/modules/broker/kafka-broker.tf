# Kafka Broker module for system tests (SASL-PLAINTEXT without TLS)

locals {
  common_labels = {
    "app.kubernetes.io/name"    = "kafka-e2e-test"
    "app.kubernetes.io/part-of" = "dataspace-ecosystem"
  }

   kafka_proxy_image = (
    var.environment == "local" ? "localhost/kafka-proxy-entra-auth:latest" :
    var.environment == "devbox" ? "${var.devbox-registry}/kafka-proxy-entra-auth:latest" :
    "kafka-proxy-entra-auth:latest"
  )
}


resource "kubernetes_deployment" "kafka_broker" {
  metadata {
    name = "broker"
    labels = merge(local.common_labels, {
      "app" = "broker"
    })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "broker"
      }
    }

    template {
      metadata {
        labels = {
          app = "broker"
        }
      }

      spec {
        container {
          name  = "broker"
          image = "apache/kafka:4.0.0"

          port {
            container_port = 9092
            name           = "kafka-plain"
          }
          port {
            container_port = 19093
            name           = "controller"
          }

          env {
            name  = "KAFKA_NODE_ID"
            value = "1"
          }
          env {
            name  = "KAFKA_PROCESS_ROLES"
            value = "broker,controller"
          }
          env {
            name  = "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"
            value = "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
          }
          env {
            name  = "KAFKA_LISTENERS"
            value = "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:19093"
          }
          env {
            name  = "KAFKA_ADVERTISED_LISTENERS"
            value = "PLAINTEXT://broker.default.svc.cluster.local:9092"
          }
          env {
            name  = "KAFKA_CONTROLLER_LISTENER_NAMES"
            value = "CONTROLLER"
          }
          env {
            name  = "KAFKA_INTER_BROKER_LISTENER_NAME"
            value = "PLAINTEXT"
          }
          env {
            name  = "KAFKA_CONTROLLER_QUORUM_VOTERS"
            value = "1@localhost:19093"
          }
          env {
            name  = "KAFKA_LOG_DIRS"
            value = "/tmp/kraft-combined-logs"
          }
          env {
            name  = "CLUSTER_ID"
            value = "clusterA"
          }
          env {
            name  = "KAFKA_BROKER_ID"
            value = "1"
          }
          env {
            name  = "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR"
            value = "1"
          }
          env {
            name  = "KAFKA_DEFAULT_REPLICATION_FACTOR"
            value = "1"
          }
          env {
            name  = "KAFKA_MIN_INSYNC_REPLICAS"
            value = "1"
          }
          env {
            name  = "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR"
            value = "1"
          }
          env {
            name  = "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR"
            value = "1"
          }
          env {
            name  = "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS"
            value = "0"
          }

          volume_mount {
            name       = "kafka-logs"
            mount_path = "/tmp/kraft-combined-logs"
          }

          resources {
            requests = {
              memory = "512Mi"
              cpu    = "250m"
            }
            limits = {
              memory = "1Gi"
              cpu    = "500m"
            }
          }

          liveness_probe {
            tcp_socket {
              port = 9092
            }
            initial_delay_seconds = 60
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          readiness_probe {
            tcp_socket {
              port = 9092
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }
        }

        volume {
          name = "kafka-logs"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "kafka_broker" {
  metadata {
    name = "broker"
    labels = merge(local.common_labels, {
      "app" = "broker"
    })
  }

  spec {
    type = "ClusterIP"

    port {
      port        = 9092
      target_port = 9092
      protocol    = "TCP"
      name        = "kafka-plain"
    }
    port {
      port        = 19093
      target_port = 19093
      protocol    = "TCP"
      name        = "controller"
    }

    selector = {
      app = "broker"
    }
  }
}

# Create self-signed TLS certificates for proxy provider
resource "tls_private_key" "proxy_provider_ca" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "proxy_provider_ca" {
  private_key_pem = tls_private_key.proxy_provider_ca.private_key_pem

  subject {
    common_name  = "Kafka Proxy Provider CA"
    organization = "Dataspace Ecosystem Test"
  }

  validity_period_hours = 8760 # 1 year

  is_ca_certificate = true

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "cert_signing",
  ]
}

resource "tls_private_key" "proxy_provider_server" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_cert_request" "proxy_provider_server" {
  private_key_pem = tls_private_key.proxy_provider_server.private_key_pem

  subject {
    common_name  = "proxy-provider"
    organization = "Dataspace Ecosystem Test"
  }

  dns_names = [
    "proxy-provider",
    "proxy-provider.default",
    "proxy-provider.default.svc",
    "proxy-provider.default.svc.cluster.local",
    "proxy-provider-oauth2",
    "proxy-provider-oauth2.default",
    "proxy-provider-oauth2.default.svc",
    "proxy-provider-oauth2.default.svc.cluster.local",
    "localhost"
  ]

  ip_addresses = [
    "127.0.0.1"
  ]
}

resource "tls_locally_signed_cert" "proxy_provider_server" {
  cert_request_pem   = tls_cert_request.proxy_provider_server.cert_request_pem
  ca_private_key_pem = tls_private_key.proxy_provider_ca.private_key_pem
  ca_cert_pem        = tls_self_signed_cert.proxy_provider_ca.cert_pem

  validity_period_hours = 8760 # 1 year

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

# Create Kubernetes secrets for TLS certificates
resource "kubernetes_secret" "proxy_provider_tls_ca" {
  metadata {
    name   = "proxy-provider-tls-ca"
    labels = local.common_labels
  }

  data = {
    "ca.crt" = tls_self_signed_cert.proxy_provider_ca.cert_pem
  }

  type = "Opaque"
}

resource "kubernetes_secret" "proxy_provider_tls_server" {
  metadata {
    name   = "proxy-provider-tls-server"
    labels = local.common_labels
  }

  data = {
    "tls.crt" = tls_locally_signed_cert.proxy_provider_server.cert_pem
    "tls.key" = tls_private_key.proxy_provider_server.private_key_pem
  }

  type = "kubernetes.io/tls"
}

# Create ConfigMap for CA certificate (for easy access by clients)
resource "kubernetes_config_map" "proxy_provider_tls_ca" {
  metadata {
    name   = "proxy-provider-tls-ca"
    labels = local.common_labels
  }

  data = {
    "ca.crt" = tls_self_signed_cert.proxy_provider_ca.cert_pem
  }
}

# Deploy Proxy Provider (with TLS)
resource "kubernetes_deployment" "proxy_provider" {
  metadata {
    name = "kafka-proxy-provider"
    labels = merge(local.common_labels, {
      "app" = "kafka-proxy-provider"
    })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "kafka-proxy-provider"
      }
    }

    template {
      metadata {
        labels = {
          app = "kafka-proxy-provider"
        }
      }

      spec {
        dynamic "image_pull_secrets" {
          for_each = var.environment == "devbox" && var.devbox-registry-cred != "" ? [1] : []
          content {
            name = var.devbox-registry-cred
          }
        }

        container {
          name  = "kafka-proxy-provider"
          image = local.kafka_proxy_image
          image_pull_policy = var.environment == "local" ? "Never" : "IfNotPresent"

          port {
            container_port = 30001
            name           = "proxy-port"
          }
          port {
            container_port = 30002
            name           = "proxy-tls-port"
          }

          args = [
            "server",
            # Main bootstrap server mapping (TLS)
            "--bootstrap-server-mapping=broker:9092,0.0.0.0:30001,proxy-provider:30001",
            "--debug-enable",
            "--dynamic-advertised-listener=proxy-provider:30001",
            "--auth-local-enable",
            "--auth-local-mechanism=PLAIN",
            "--auth-local-command=/usr/local/bin/entra-token-verifier",
            "--auth-local-param=--tenant-id=<tenant-id>",
            "--auth-local-param=--client-id=<client-id>",
            "--auth-local-param=--static-user=provider1:secret1",
            "--auth-local-param=--debug",
            # TLS listener configuration
            "--proxy-listener-tls-enable",
            "--proxy-listener-cert-file=/etc/tls/server/tls.crt",
            "--proxy-listener-key-file=/etc/tls/server/tls.key",
          ]

          volume_mount {
            name       = "tls-ca"
            mount_path = "/etc/tls/ca"
            read_only  = true
          }

          volume_mount {
            name       = "tls-server"
            mount_path = "/etc/tls/server"
            read_only  = true
          }

          resources {
            requests = {
              memory = "128Mi"
              cpu    = "100m"
            }
            limits = {
              memory = "256Mi"
              cpu    = "200m"
            }
          }

          liveness_probe {
            tcp_socket {
              port = 30001
            }
            initial_delay_seconds = 30
            period_seconds        = 10
          }

          readiness_probe {
            tcp_socket {
              port = 30001
            }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }

        volume {
          name = "tls-ca"
          secret {
            secret_name = kubernetes_secret.proxy_provider_tls_ca.metadata[0].name
          }
        }

        volume {
          name = "tls-server"
          secret {
            secret_name = kubernetes_secret.proxy_provider_tls_server.metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [kubernetes_deployment.kafka_broker]
}

# Deploy Kafkacat for testing connectivity
resource "kubernetes_deployment" "kafkacat" {
  metadata {
    name = "kafkacat"
    labels = merge(local.common_labels, {
      "app" = "kafkacat"
    })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "kafkacat"
      }
    }

    template {
      metadata {
        labels = {
          app = "kafkacat"
        }
      }

      spec {
        container {
          name  = "kafkacat"
          image = "edenhill/kcat:1.7.1"

          # Keep container running for interactive testing
          command = ["/bin/sh"]
          args    = ["-c", "while true; do sleep 30; done"]

          env {
            name  = "KAFKA_BROKER_HOST"
            value = "broker"
          }
          env {
            name  = "KAFKA_BROKER_PORT"
            value = "9092"
          }
          env {
            name  = "PROXY_HOST"
            value = "proxy-provider"
          }
          env {
            name  = "PROXY_PORT"
            value = "30001"
          }

          resources {
            requests = {
              memory = "64Mi"
              cpu    = "50m"
            }
            limits = {
              memory = "128Mi"
              cpu    = "100m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_deployment.kafka_broker, kubernetes_deployment.proxy_provider]
}

resource "kubernetes_service" "proxy_provider" {
  metadata {
    name = "proxy-provider"
    labels = merge(local.common_labels, {
      "app" = "kafka-proxy-provider"
    })
  }

  spec {
    type = "ClusterIP"

    port {
      port        = 30001
      target_port = 30001
      protocol    = "TCP"
      name        = "proxy-port"
    }
    port {
      port        = 30002
      target_port = 30002
      protocol    = "TCP"
      name        = "proxy-tls-port"
    }

    selector = {
      app = "kafka-proxy-provider"
    }
  }
}

# Create OAuth2 validation credentials secret (verifier only needs tenant_id and client_id)
resource "kubernetes_secret" "oauth2_validation_credentials" {
  metadata {
    name   = "oauth2-validation-credentials"
    labels = local.common_labels
  }

  data = {
    tenant_id = "<your-tenant-id>"
    client_id = "<your-broker-client-id>"
  }

  type = "Opaque"
}

# Deploy Proxy Provider with OAuth2 Validation (port 30003)
resource "kubernetes_deployment" "proxy_provider_oauth2" {
  metadata {
    name = "kafka-proxy-provider-oauth2"
    labels = merge(local.common_labels, {
      "app" = "kafka-proxy-provider-oauth2"
    })
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "kafka-proxy-provider-oauth2"
      }
    }

    template {
      metadata {
        labels = {
          app = "kafka-proxy-provider-oauth2"
        }
      }

      spec {
        dynamic "image_pull_secrets" {
          for_each = var.environment == "devbox" && var.devbox-registry-cred != "" ? [1] : []
          content {
            name = var.devbox-registry-cred
          }
        }

        container {
          name              = "kafka-proxy-provider-oauth2"
          image             = local.kafka_proxy_image
          image_pull_policy = var.environment == "local" ? "Never" : "IfNotPresent"

          port {
            container_port = 30003
            name           = "proxy-oauth2"
          }

          env {
            name = "OAUTH2_TENANT_ID"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.oauth2_validation_credentials.metadata[0].name
                key  = "tenant_id"
              }
            }
          }

          env {
            name = "OAUTH2_CLIENT_ID"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.oauth2_validation_credentials.metadata[0].name
                key  = "client_id"
              }
            }
          }

          args = [
            "server",
            # Main bootstrap server mapping (OAuth2)
            "--bootstrap-server-mapping=broker:9092,0.0.0.0:30003,proxy-provider-oauth2:30003",
            "--debug-enable",
            "--dynamic-advertised-listener=proxy-provider-oauth2:30003",
            # OAuth2 token validation using entra-token-info plugin
            # Uses OAUTHBEARER mechanism - proper OAuth2 flow with token verification
            # The entra-token-info plugin implements the TokenInfo interface for OAUTHBEARER
            "--auth-local-enable",
            "--auth-local-mechanism=OAUTHBEARER",
            "--auth-local-command=/usr/local/bin/entra-token-info",
            "--auth-local-param=--tenant-id=<tenant-id>",
            "--auth-local-param=--client-id=<client-id>",
            "--auth-local-param=--debug",
            # TLS listener configuration
            "--proxy-listener-tls-enable",
            "--proxy-listener-cert-file=/etc/tls/server/tls.crt",
            "--proxy-listener-key-file=/etc/tls/server/tls.key",
          ]

          volume_mount {
            name       = "tls-server"
            mount_path = "/etc/tls/server"
            read_only  = true
          }

          resources {
            requests = {
              memory = "128Mi"
              cpu    = "100m"
            }
            limits = {
              memory = "256Mi"
              cpu    = "200m"
            }
          }

          liveness_probe {
            tcp_socket {
              port = 30003
            }
            initial_delay_seconds = 30
            period_seconds        = 10
          }

          readiness_probe {
            tcp_socket {
              port = 30003
            }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }

        volume {
          name = "tls-server"
          secret {
            secret_name = kubernetes_secret.proxy_provider_tls_server.metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [kubernetes_deployment.kafka_broker]
}

resource "kubernetes_service" "proxy_provider_oauth2" {
  metadata {
    name = "proxy-provider-oauth2"
    labels = merge(local.common_labels, {
      "app" = "kafka-proxy-provider-oauth2"
    })
  }

  spec {
    type = "ClusterIP"

    port {
      port        = 30003
      target_port = 30003
      protocol    = "TCP"
      name        = "proxy-oauth2"
    }

    selector = {
      app = "kafka-proxy-provider-oauth2"
    }
  }
}

# Output the service details for use in tests
output "kafka_broker_service_name" {
  value = kubernetes_service.kafka_broker.metadata[0].name
}


output "kafka_broker_port" {
  value = 9092
}

output "kafka_broker_host" {
  value = "broker.default.svc.cluster.local"
}

output "proxy_provider_service_name" {
  value = kubernetes_service.proxy_provider.metadata[0].name
}

output "proxy_provider_port" {
  value = 30001
}

output "proxy_provider_tls_port" {
  value = 30002
}

output "proxy_provider_host" {
  value = "proxy-provider.default.svc.cluster.local"
}

output "proxy_provider_ca_cert" {
  value     = tls_self_signed_cert.proxy_provider_ca.cert_pem
  sensitive = true
}

output "proxy_provider_ca_configmap" {
  value = kubernetes_config_map.proxy_provider_tls_ca.metadata[0].name
}

output "kafkacat_deployment_name" {
  value = kubernetes_deployment.kafkacat.metadata[0].name
}

output "proxy_provider_oauth2_service_name" {
  value = kubernetes_service.proxy_provider_oauth2.metadata[0].name
}

output "proxy_provider_oauth2_port" {
  value = 30003
}

output "proxy_provider_oauth2_host" {
  value = "proxy-provider-oauth2.default.svc.cluster.local"
}

output "oauth2_tenant_id" {
  value     = kubernetes_secret.oauth2_validation_credentials.data.tenant_id
  sensitive = true
}

output "oauth2_client_id" {
  value     = kubernetes_secret.oauth2_validation_credentials.data.client_id
  sensitive = true
}