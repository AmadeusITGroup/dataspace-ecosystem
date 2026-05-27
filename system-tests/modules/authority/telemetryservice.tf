locals {
  telemetryservice_release_name = "${var.authority_name}-telemetryservice"
  connection_string_alias       = "event-hub-connection-string"
  telemetry_service_image = (
    var.environment == "local" ? "localhost/telemetry-service-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/telemetry-service-postgresql-hashicorpvault" :
    "telemetry-service-postgresql-hashicorpvault"
  )

  credential_url = "${local.scheme}://${local.telemetryservice_release_name}:8181/api/credential"
}

resource "helm_release" "telemetryservice" {
  name              = local.telemetryservice_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = "../charts"
  chart             = "telemetry-service"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "telemetryservice" : {
        "initContainers" : var.tls_enabled ? [
          {
            "name" : "keystore-setup",
            "image" : "${local.telemetry_service_image}:latest",
            "imagePullPolicy" : local.image_pull_policy,
            "command" : ["/bin/sh", "-c"],
            "args" : [
              "cp /etc/pki/ca-trust/extracted/java/cacerts /opt/ca/cacerts && chmod 666 /opt/ca/cacerts && keytool -import -trustcacerts -keystore /opt/ca/cacerts -storepass changeit -noprompt -alias internalCa -file /certs/ca.crt && openssl pkcs12 -export -in /certs/tls.crt -inkey /certs/tls.key -out /opt/ca/keystore.p12 -passout pass:changeit -name service"
            ],
            "volumeMounts" : [
              { "name" : "internal-tls-volume", "mountPath" : "/certs" },
              { "name" : "shared-volume", "mountPath" : "/opt/ca" }
            ]
          }
        ] : [],
        "image" : {
          "repository" : local.telemetry_service_image
          "pullPolicy" : local.image_pull_policy
          "tag" : "latest"
        },
        "sts" : {
          "tokenUrl" : local.sts_url
          "clientId" : local.did_url,
          "clientSecretAlias" : local.sts_client_secret_alias
        }
        "did" : {
          "web" : {
            "url" : local.did_url
            "useHttps" : var.tls_enabled
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
edc.iam.trusted-issuer.authority.id=${local.authority_did}
edc.vault.hashicorp.token.scheduled-renew-enabled=false
edc.vault.hashicorp.allow.fallback=true
dse.namespace.prefix=${var.dse_namespace_prefix}
dse.policy.prefix=${var.dse_policy_prefix}
${local.tls_config_props}
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : local.tls_java_opts
          "EDC_IAM_CREDENTIAL_REVOCATION_MIMETYPE" : "application/json"
        }

        "credentialfactory" : {
          "azure" : {
            "eventhub" : {
              "connectionstring" : {
                "alias" : local.connection_string_alias
              }
            }
          }
        }

        "ingress" : {
          "enabled" : true
          "className" : "nginx"
          "annotations" : merge(
            {
              "nginx.ingress.kubernetes.io/ssl-redirect" : "false"
              "nginx.ingress.kubernetes.io/use-regex" : "true"
              "nginx.ingress.kubernetes.io/rewrite-target" : "/api/$1$2"
            },
            var.tls_enabled ? {
              "nginx.ingress.kubernetes.io/backend-protocol" : "HTTPS"
              "nginx.ingress.kubernetes.io/proxy-ssl-verify" : "on"
              "nginx.ingress.kubernetes.io/proxy-ssl-secret" : "default/${var.ingress_proxy_ssl_ca_secret_name}"
              "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.telemetryservice_release_name}.default.svc.cluster.local"
              "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
              } : {
              "nginx.ingress.kubernetes.io/backend-protocol" : "HTTP"
            }
          ),
          "hostname" : "localhost"
          "tls" : {
            "enabled" : var.tls_enabled
            "secretName" : var.tls_enabled ? var.ingress_tls_secret_name : ""
          },
          "endpoints" : [
            {
              "port" : 8181,
              "path" : "/${var.authority_name}/ts/(credential)(.*)",
              "pathType" : "ImplementationSpecific"
            }
          ]
        },
        "postgresql" : {
          "jdbcUrl" : "jdbc:postgresql://${var.db_server_fqdn}/${local.db_name}",
          "credentials" : {
            "secret" : {
              "name" : kubernetes_secret.db-user-credentials.metadata.0.name
            }
          }
        },
        "internalTls" : {
          "enabled" : var.tls_enabled
          "secretName" : var.tls_enabled ? var.internal_tls_secret_name : ""
        }

        "vault" : {
          "hashicorp" : {
            "url" : module.vault.vault_url
            "token" : {
              "secret" : {
                "name" : module.vault.vault_secret_name
                "tokenKey" : "rootToken"
              }
            }
          }
        }
      }
    })
  ]

  depends_on = [module.vault, module.db]
}
