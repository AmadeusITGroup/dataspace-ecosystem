locals {
  issuerservice_release_name = "${var.authority_name}-issuerservice"
  issuerservice_image = (
    var.environment == "local" ? "localhost/issuer-service-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/issuer-service-postgresql-hashicorpvault" :
    "issuer-service-postgresql-hashicorpvault"
  )

  issuance_url = "${local.scheme}://${local.issuerservice_release_name}:8282/api/issuance"
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
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "issuerservice" : {
        "initContainers" : var.tls_enabled ? [
          {
            "name" : "keystore-setup",
            "image" : "${local.issuerservice_image}:latest",
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
          "repository" : local.issuerservice_image
          "pullPolicy" : local.image_pull_policy
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
            "useHttps" : var.tls_enabled
          }
        },
        domain : "route",
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
edc.vault.hashicorp.allow.fallback=true
${local.tls_config_props}
        EOT

        "env" : {
          "JAVA_TOOL_OPTIONS" : local.tls_java_opts
          "EDC_STATUSLIST_CALLBACK_ADDRESS" : "${local.scheme}://${local.issuerservice_release_name}:9999/api/statuslist"
          "EDC_IAM_CREDENTIAL_REVOCATION_MIMETYPE" : "application/json"
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
              "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.issuerservice_release_name}.default.svc.cluster.local"
              "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
              } : {
              "nginx.ingress.kubernetes.io/backend-protocol" : "HTTP"
            }
          ),
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
          "hostname" : "localhost"
          "tls" : {
            "enabled" : var.tls_enabled
            "secretName" : var.tls_enabled ? var.ingress_tls_secret_name : ""
          }
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

      }
    })
  ]

  depends_on = [module.vault, module.db]
}
