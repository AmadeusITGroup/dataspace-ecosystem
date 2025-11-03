locals {
  identityhub_release_name = "${var.authority_name}-identityhub"
  did_url                  = "did:web:${local.identityhub_release_name}%3A8383:api:did"
  authority_identity_hub_image = (
    var.environment == "local" ? "localhost/identity-hub-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/identity-hub-postgresql-hashicorpvault" :
    "identity-hub-postgresql-hashicorpvault"
  )

  sts_port                = 8484
  sts_path                = "/api/sts"
  sts_url                 = "http://${local.identityhub_release_name}:${local.sts_port}${local.sts_path}/token"
  sts_client_secret_alias = "${local.did_url}-sts-client-secret"
  did_url_base64_url      = replace(replace(replace(base64encode(local.did_url), "+", "-"), "/", "_"), "=", "")

  identityhub_credentials_url = "http://${local.identityhub_release_name}:8282/api/credentials"
}

##################
## IDENTITY HUB ##
##################

resource "helm_release" "identity-hub" {
  name              = local.identityhub_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = "../charts"
  chart             = "identity-hub"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "identityhub" : {
        "initContainers" : [],
        "image" : {
          "repository" : local.authority_identity_hub_image
          "tag" : "latest"
          "pullPolicy" : var.environment == "local" ? "Never" : "IfNotPresent"
        },
        "keys" : {
          "sts" : {
            "privateKeyAlias" : local.privatekey_alias,
            "publicKeyAlias" : local.publickey_alias,
            "publicKeyId" : "${local.did_url}#my-key"
          }
        },
        "participantcontext" : {
          "superuser" : {
            "services" : jsonencode([
              {
                id : "credential-service-url"
                type : "CredentialService",
                serviceEndpoint : "${local.identityhub_credentials_url}/v1/participants/${local.did_url_base64_url}"
              },
              {
                id : "issuer-service-url",
                type : "IssuerService",
                serviceEndpoint : "${local.issuance_url}/v1alpha/participants/${local.did_url_base64_url}"
              },
              {
                id : "telemetry-service-url",
                type : "TelemetryServiceCredential",
                serviceEndpoint : local.credential_url
              }
            ])
          }
        }
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : false
          }
        },

        "api" : {
          "cors" : {
            "enabled" : true
          }
        }

        #        "logging" : <<EOT
        #.level=INFO
        #org.eclipse.edc.level=ALL
        #handlers=java.util.logging.ConsoleHandler
        #java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        #java.util.logging.ConsoleHandler.level=ALL
        #java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
        #        EOT

        "config" : <<EOT
edc.vault.hashicorp.token.scheduled-renew-enabled=false
        EOT
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
          },
          "endpoints" : [
            {
              "port" : 8181,
              "path" : "/${var.authority_name}/ih/(identity)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8282,
              "path" : "/${var.authority_name}/ih/(credentials)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8383,
              "path" : "/${var.authority_name}/ih/(did)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8484,
              "path" : "/${var.authority_name}/ih/(sts)(.*)",
              "pathType" : "ImplementationSpecific"
            }
          ]
        },
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
