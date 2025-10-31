locals {
  issuerservice_release_name = "${var.authority_name}-issuerservice"
  issuerservice_image = (
    var.environment == "local" ? "localhost/issuer-service-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/issuer-service-postgresql-hashicorpvault" :
    "issuer-service-postgresql-hashicorpvault"
  )

  issuance_url = "http://${local.issuerservice_release_name}:8282/api/issuance"
}

resource "helm_release" "issuerservice" {
  name              = local.issuerservice_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = "../charts"
  chart             = "issuer-service"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets": var.environment == "devbox" ? [
        {
          "name": var.devbox-registry-cred
        }
      ] : []
      "issuerservice" : {
        "initContainers" : [],
        "image" : {
          "repository" : local.issuerservice_image
          "pullPolicy" : var.environment == "local" ? "Never" : "IfNotPresent"
          "tag" : "latest"
        },
        "keys" : {
          "sts" : {
            "privateKeyAlias" : local.publickey_alias
            "publicKeyId" : "${local.did_url}#my-key"
          }
          "statuslist" : {
            "privateKeyAlias" : local.privatekey_alias,
          }
        },
        "did" : {
          "web" : {
            "url" : local.did_url
            "useHttps" : false
          }
        },
        domain: "route",
        "logging" : <<EOT
        .level=DEBUG
        org.eclipse.edc.level=ALL
        handlers=java.util.logging.ConsoleHandler
        java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        java.util.logging.ConsoleHandler.level=ALL
        java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
               EOT

        "config" : <<EOT
edc.vault.hashicorp.token.scheduled-renew-enabled=false
        EOT

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
              "port" : 8181,
              "path" : "/${var.authority_name}/is/(issueradmin)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8282,
              "path" : "/${var.authority_name}/is/(issuance)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8484,
              "path" : "/${var.authority_name}/is/(sts)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 9999,
              "path" : "/${var.authority_name}/is/(statuslist)(.*)",
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

  depends_on = [module.vault, module.db]
}
