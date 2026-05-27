locals {
  identityhub_release_name = "${var.participant_name}-identityhub"

  did_url                 = var.selfhosted_did_url != "" ? var.selfhosted_did_url : "did:web:${local.identityhub_release_name}%3A8383:api:did"
  sts_port                = 8484
  sts_path                = "/api/sts"
  sts_url                 = var.selfhosted_sts_url != "" ? var.selfhosted_sts_url : "${local.scheme}://${local.identityhub_release_name}:${local.sts_port}${local.sts_path}/token"
  sts_client_secret_alias = "${local.did_url}-sts-client-secret"
  did_url_base64_url      = replace(replace(replace(base64encode(local.did_url), "+", "-"), "/", "_"), "=", "")

  participant_identity_hub_image = (
    var.environment == "local" ? "localhost/identity-hub-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/identity-hub-postgresql-hashicorpvault" :
    var.environment == "selfhosted" ? var.identity_hub_image :
    "identity-hub-postgresql-hashicorpvault"
  )

  identityhub_credentials_url = "${local.scheme}://${local.identityhub_release_name}:8282/api/credentials"
}

resource "helm_release" "identity-hub" {
  name              = local.identityhub_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = var.charts_path
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
        "initContainers" : var.tls_enabled ? [
          {
            "name" : "keystore-setup",
            "image" : "${local.participant_identity_hub_image}:latest",
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
          "repository" : local.participant_identity_hub_image
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
            "key" : "${local.did_url_base64_url}.root"
            "services" : jsonencode([
              {
                id : "dsp-url"
                type : "DSPMessaging",
                serviceEndpoint : local.protocol_url
              },
              {
                id : "credential-service-url"
                type : "CredentialService",
                serviceEndpoint : "${local.identityhub_credentials_url}/v1/participants/${local.did_url_base64_url}"
              }
            ])
          }
        }
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : var.tls_enabled
          }
        },
        "config" : <<EOT
edc.vault.hashicorp.token.scheduled-renew-enabled=false
edc.vault.hashicorp.allow.fallback=true
${local.tls_config_props}
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : local.tls_java_opts
          "EDC_IAM_CREDENTIAL_REVOCATION_MIMETYPE" : "application/json"
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
              "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.identityhub_release_name}.default.svc.cluster.local"
              "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
              } : {
              "nginx.ingress.kubernetes.io/backend-protocol" : "HTTP"
            }
          ),
          "endpoints" : [
            {
              "port" : 8181,
              "path" : "${var.participant_with_prefix}/ih/(identity)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8282,
              "path" : "${var.participant_with_prefix}/ih/(credentials)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8383,
              "path" : "${var.participant_with_prefix}/ih/(did)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8484,
              "path" : "${var.participant_with_prefix}/ih/(sts)(.*)",
              "pathType" : "ImplementationSpecific"
            }
          ]
          "hostname" : "localhost"
          "tls" : {
            "enabled" : var.tls_enabled
            "secretName" : var.tls_enabled ? var.ingress_tls_secret_name : ""
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
                "name" : module.vault.vault_secret_name,
                "tokenKey" : "rootToken"
              }
            }
          }
        }

        "api" : {
          "cors" : {
            "enabled" : true
          }
        }

        "logging" : <<EOT
        .level=DEBUG
        org.eclipse.edc.level=ALL
        handlers=java.util.logging.ConsoleHandler
        java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
        java.util.logging.ConsoleHandler.level=ALL
        java.util.logging.SimpleFormatter.format=[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n
               EOT
      }

    })
  ]

  depends_on = [module.vault, module.db]
}
