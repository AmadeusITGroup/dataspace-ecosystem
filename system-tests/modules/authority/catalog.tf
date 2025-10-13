locals {
  catalog_release_name = "${var.authority_name}-federatedcatalog"

  crawler_initial_delay    = 10
  crawler_execution_period = 10
  federated_catalog_image = (
    var.environment == "local" ? "localhost/federated-catalog-postgresql-hashicorpvault" :
    "federated-catalog-postgresql-hashicorpvault"
  )
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
      "federatedcatalog" : {
        "initContainers" : [],
        "image" : {
          "repository" : local.federated_catalog_image
          "tag" : "latest"
          "pullPolicy" : "Never"
        },
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : false
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
        EOT

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
          },
          "endpoints" : [
            {
              "port" : 8383,
              "path" : "/${var.authority_name}/(catalog)(.*)",
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
        }

        "api" : {
          "cors" : {
            "enabled" : true
          }
        }

        "vault" : {
          "hashicorp" : {
            "url" : module.vault.vault_url
            "token" : {
              "secret" : {
                "name" : module.vault.vault_secret_name,
                "tokenKey":"rootToken"
              }
            }
          }
        }
      }
    })
  ]

  depends_on = [module.db, module.vault]
}