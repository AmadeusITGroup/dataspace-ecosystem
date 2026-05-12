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
  sts_url                 = "https://${local.identityhub_release_name}.default.svc.cluster.local:${local.sts_port}${local.sts_path}/token"
  sts_client_secret_alias = "${local.did_url}-sts-client-secret"
  did_url_base64_url      = replace(replace(replace(base64encode(local.did_url), "+", "-"), "/", "_"), "=", "")

  identityhub_credentials_url = "https://${local.identityhub_release_name}.default.svc.cluster.local:8282/api/credentials"
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
        "initContainers" : [
          {
            "name" : "keystore-setup",
            "image" : "${local.authority_identity_hub_image}:latest",
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
          "repository" : local.authority_identity_hub_image
          "tag" : "latest"
          "pullPolicy" : local.image_pull_policy
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
              },
              {
                id : "catalog-filter-url",
                type : "FederatedCatalogFilterService",
                serviceEndpoint : local.filter_url
              },
              {
                id : "federated-catalog",
                type : "FederatedCatalogService",
                serviceEndpoint : local.catalog_url
              }
            ])
          }
        }
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : true
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
edc.web.https.keystore.path=/shared/keystore.p12
edc.web.https.keystore.type=PKCS12
edc.web.https.keystore.password=changeit
edc.web.https.keymanager.password=changeit
edc.vault.hashicorp.allow.fallback=true
        EOT
        "env" : {
          "JAVA_TOOL_OPTIONS" : "-Djavax.net.ssl.trustStore=/shared/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
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
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.identityhub_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
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
          "hostname" : "localhost"
          "tls" : {
            "enabled" : true
            "secretName" : var.ingress_tls_secret_name
          }
        },
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
