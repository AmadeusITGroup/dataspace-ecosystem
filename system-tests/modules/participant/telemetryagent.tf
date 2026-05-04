locals {
  telemetry_agent_image = (
    var.environment == "local" ? "localhost/telemetry-agent-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/telemetry-agent-postgresql-hashicorpvault" :
    var.environment == "selfhosted" ? var.telemetry_agent_image :
    "telemetry-agent-postgresql-hashicorpvault"
  )
  namespace                    = "local-eventhub-eventhubs"
  name                         = "eh1"
  telemetry_agent_release_name = "${var.participant_name}-telemetryagent"
}

##################
## TELEMETRY AGENT ##
##################

resource "helm_release" "telemetryagent" {
  name              = local.telemetry_agent_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = var.charts_path
  chart             = "telemetry-agent"
  # version           = "latest"


  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "telemetryagent" : {
        "initContainers" : [
          {
            "name" : "keystore-setup",
            "image" : "${local.telemetry_agent_image}:latest",
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
        ],
        "image" : {
          "repository" : local.telemetry_agent_image
          "tag" : "latest"
          "pullPolicy" : local.image_pull_policy
        },
        "keys" : {
          "sts" : {
            "privateKeyVaultAlias" : local.privatekey_alias,
            "publicKeyVaultAlias" : "${local.did_url}#my-key"
          }
        },
        "did" : {
          "web" : {
            "url" : local.did_url
            "useHttps" : true
          }
        },
        "authority" : {
          "did" : local.authority_did
        }

        "sts" : {
          "tokenUrl" : local.sts_url
          "clientId" : local.did_url,
          "clientSecretAlias" : local.sts_client_secret_alias
        }

        "logging" : <<EOT
        .level=INFO
        org.eclipse.edc.level=ALL
        handlers=java.util.logging.ConsoleHandler
        java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        java.util.logging.ConsoleHandler.level=ALL
        java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
                EOT

        "config" : <<EOT
edc.vault.hashicorp.token.scheduled-renew-enabled=false
dse.namespace.prefix=${var.dse_namespace_prefix}
dse.policy.prefix=${var.dse_policy_prefix}
edc.web.https.keystore.path=/shared/keystore.p12
edc.web.https.keystore.type=PKCS12
edc.web.https.keystore.password=changeit
edc.web.https.keymanager.password=changeit
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : "-Djavax.net.ssl.trustStore=/shared/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
        }

        "credentialmanager" : {
          "privatekey" : {
            "alias" : var.participant_name
          }
        }
        "telemetryservice" : {
          "eventhub" : {
            "namespace" : local.namespace,
            "name" : local.name
          }
        }

        "postgresql" : {
          "jdbcUrl" : "jdbc:postgresql://${var.db_server_fqdn}/${local.db_name}",
          "credentials" : {
            "secret" : {
              "name" : kubernetes_secret.db-user-credentials.metadata.0.name
            }
          }
        },
        "ingress" : {
          "enabled" : true
          "className" : "nginx"
          "annotations" : {
            "nginx.ingress.kubernetes.io/ssl-redirect" : "false"
            "nginx.ingress.kubernetes.io/use-regex" : "true"
            "nginx.ingress.kubernetes.io/rewrite-target" : "/api/$1$2"
            "nginx.ingress.kubernetes.io/backend-protocol" : "HTTPS"
            "nginx.ingress.kubernetes.io/proxy-ssl-verify" : "on"
            "nginx.ingress.kubernetes.io/proxy-ssl-secret" : "default/${var.ingress_proxy_ssl_ca_secret_name}"
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.telemetry_agent_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
          },
          "hostname" : "localhost"
          "tls" : {
            "enabled" : true
            "secretName" : var.ingress_tls_secret_name
          },
        }
        "internalTls" : {
          "secretName" : var.internal_tls_secret_name
        }

        "vault" : {
          "hashicorp" : {
            "url" : module.vault.vault_url
            "token" : {
              "secret" : {
                "name" : module.vault.vault_secret_name,
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
