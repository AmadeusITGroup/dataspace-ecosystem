locals {
  catalog_release_name = "${var.authority_name}-federatedcatalog"

  crawler_initial_delay    = 10
  crawler_execution_period = 10
  federated_catalog_image = (
    var.environment == "local" ? "localhost/federated-catalog-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/federated-catalog-postgresql-hashicorpvault" :
    "federated-catalog-postgresql-hashicorpvault"
  )
  catalog_url = "https://${local.catalog_release_name}.default.svc.cluster.local:8383/api/catalog/v1alpha/catalog/query"
}

resource "helm_release" "federated-catalog" {
  name              = local.catalog_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = "../charts"
  chart             = "federated-catalog"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "federatedcatalog" : {
        "initContainers" : [
          {
            "name" : "keystore-setup",
            "image" : "${local.federated_catalog_image}:latest",
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
          "repository" : local.federated_catalog_image
          "tag" : "latest"
          "pullPolicy" : local.image_pull_policy
        },
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : true
          }
        },
        "crawler" : {
          "cache" : {
            "executionPeriodSeconds" : local.crawler_execution_period
            "executionDelaySeconds" : local.crawler_initial_delay
          }
        },

        #        "logging" : <<EOT
        #.level=INFO
        #org.eclipse.edc.level=ALL
        #handlers=java.util.logging.ConsoleHandler
        #java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        #java.util.logging.ConsoleHandler.level=ALL
        #java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
        #        EOT

        "config" : <<EOT
edc.iam.trusted-issuer.authority.id=${local.did_url}
edc.vault.hashicorp.token.scheduled-renew-enabled=false
edc.vault.hashicorp.allow.fallback=true
dse.namespace.prefix=${var.dse_namespace_prefix}
dse.policy.prefix=${var.dse_policy_prefix}
edc.web.https.keystore.path=/shared/keystore.p12
edc.web.https.keystore.type=PKCS12
edc.web.https.keystore.password=changeit
edc.web.https.keymanager.password=changeit
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : "-Djavax.net.ssl.trustStore=/shared/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
          "EDC_IAM_CREDENTIAL_REVOCATION_MIMETYPE" : "application/json"
        }

        "sts" : {
          "tokenUrl" : local.sts_url,
          "clientId" : local.did_url,
          "clientSecretAlias" : local.sts_client_secret_alias
        }

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
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.catalog_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
          },
          "endpoints" : [
            {
              "port" : 8383,
              "path" : "/${var.authority_name}/(catalog)(.*)",
              "pathType" : "ImplementationSpecific"
            }
          ]
          "hostname" : "localhost"
          "tls" : {
            "enabled" : true
            "secretName" : var.ingress_tls_secret_name
          }
        },

        "postgresql" : {
          "jdbcUrl" : "jdbc:postgresql://${var.db_server_fqdn}/${local.db_name}",
          "credentials" : {
            "secret" : {
              "name" : kubernetes_secret.db-user-credentials.metadata.0.name
            }
          }
        }

        "api" : {
          "cors" : {
            "enabled" : true
          }
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

  depends_on = [module.db, module.vault]
}
