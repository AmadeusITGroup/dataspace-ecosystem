locals {
  telemetrystorage_release_name = "${var.authority_name}-telemetrystorage"
  telemetry_storage_image = (
    var.environment == "local" ? "localhost/telemetry-storage" :
    var.environment == "devbox" ? "${var.devbox-registry}/telemetry-storage" :
    "telemetry-storage"
  )
}

resource "helm_release" "telemetrystorage" {
  name              = local.telemetrystorage_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = "../charts"
  chart             = "telemetry-storage"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "telemetrystorage" : {
        "initContainers" : [
          {
            "name" : "keystore-setup",
            "image" : "${local.telemetry_storage_image}:latest",
            "imagePullPolicy" : var.environment == "local" ? "Never" : "IfNotPresent",
            "command" : ["/bin/sh", "-c"],
            "args" : [
              "cp /etc/pki/ca-trust/extracted/java/cacerts /opt/ca/cacerts && chmod 666 /opt/ca/cacerts && keytool -import -trustcacerts -keystore /opt/ca/cacerts -storepass changeit -noprompt -alias internalCa -file /certs/ca.crt && openssl pkcs12 -export -in /certs/tls.crt -inkey /certs/tls.key -out /opt/ca/keystore.p12 -passout pass:changeit -name service"
            ],
            "volumeMounts" : [
              { "name" : "internal-tls-volume", "mountPath" : "/certs" },
              { "name" : "shared-volume", "mountPath" : "/opt/ca" }
            ]
          }
        ],
        "image" : {
          "repository" : local.telemetry_storage_image
          "pullPolicy" : var.environment == "local" ? "Never" : "IfNotPresent"
          "tag" : "latest"
        },
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : true
          }
        },

        "logging" : <<EOT
        .level=DEBUG
        org.eclipse.edc.level=ALL
        handlers=java.util.logging.ConsoleHandler
        java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        java.util.logging.ConsoleHandler.level=ALL
        java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
               EOT

        "config" : <<EOT
edc.web.https.keystore.path=/shared/keystore.p12
edc.web.https.keystore.type=PKCS12
edc.web.https.keystore.password=changeit
edc.web.https.keymanager.password=changeit
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : "-Djavax.net.ssl.trustStore=/shared/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
        }

        "ingress" : {
          "enabled" : true
          "className" : "nginx"
          "annotations" : {
            "nginx.ingress.kubernetes.io/ssl-redirect" : "false"
            "nginx.ingress.kubernetes.io/use-regex" : "true"
            "nginx.ingress.kubernetes.io/rewrite-target" : "/api/telemetry-events"
            "nginx.ingress.kubernetes.io/backend-protocol" : "HTTPS"
            "nginx.ingress.kubernetes.io/proxy-ssl-verify" : "on"
            "nginx.ingress.kubernetes.io/proxy-ssl-secret" : "default/${var.ingress_proxy_ssl_ca_secret_name}"
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.telemetrystorage_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
          },
          "hostname" : "localhost"
          "tls" : {
            "enabled" : true
            "secretName" : var.ingress_tls_secret_name
          },
          "endpoints" : [
            {
              "port" : 8080,
              "path" : "/${var.authority_name}/telemetry-events",
              "pathType" : "ImplementationSpecific"
            }
          ]
        },
        "internalTls" : {
          "secretName" : var.internal_tls_secret_name
        }

        "postgresql" : {
          "jdbcUrl" : "jdbc:postgresql://${var.db_server_fqdn}/${local.db_billing_name}",
          "credentials" : {
            "secret" : {
              "name" : kubernetes_secret.billing-db-user-credentials.metadata.0.name
            }
          }
        }
      }
    })
  ]

  depends_on = [module.db]
}


resource "kubernetes_config_map" "billing-db-insert-script" {

  metadata {
    name = "billing-db-insert-script"
  }

  data = {
    "insert_rows.sh" = file("${path.module}/insert_rows.sh")
  }
}

resource "kubernetes_job_v1" "billing-db-insert" {
  metadata {
    name = "billing-db-insert"
  }
  spec {
    template {
      metadata {}
      spec {
        container {
          name  = "postgres"
          image = "postgres:15.3-alpine3.18"
          command = [
            "sh",
            "/db/scripts/insert_rows.sh"
          ]

          env {
            name = "POSTGRES_USER"
            value_from {
              secret_key_ref {
                name     = var.postgres_admin_credentials_secret_name
                key      = "POSTGRES_USER"
                optional = false
              }
            }
          }

          env {
            name = "PGPASSWORD"
            value_from {
              secret_key_ref {
                name     = var.postgres_admin_credentials_secret_name
                key      = "POSTGRES_PASSWORD"
                optional = false
              }
            }
          }

          env {
            name = "POSTGRES_DB"
            value_from {
              secret_key_ref {
                name     = var.postgres_admin_credentials_secret_name
                key      = "POSTGRES_DB"
                optional = false
              }
            }
          }

          env {
            name  = "DB_USER"
            value = local.db_billing_user
          }

          env {
            name  = "DB_NAME"
            value = local.db_billing_name
          }

          env {
            name  = "DB_FQDN"
            value = var.db_server_fqdn
          }

          env {
            name  = "DB_PASSWORD"
            value = local.db_user_password
          }

          volume_mount {
            mount_path = "/db/scripts"
            name       = "billing-db-insert-script"
          }
        }

        volume {
          name = "billing-db-insert-script"
          config_map {
            name = kubernetes_config_map.billing-db-insert-script.metadata.0.name
          }
        }

        restart_policy = "OnFailure"
      }
    }
    backoff_limit = 4
  }
  wait_for_completion = true
  depends_on          = [helm_release.telemetrystorage]
}
