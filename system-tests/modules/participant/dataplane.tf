locals {
  dataplane_release_name = "${var.participant_name}-dataplane"

  dpf_selector_url = "${local.scheme}://${local.controlplane_release_name}:8383/api/control/v1/dataplanes"


  ##################
  ## IMPORT NOTE! ##
  ############################################################################################
  # These URLs must be the external routes exposed by the participant over the public internet
  # which, are typically exposed through an API gateway, an external Load Balancer...
  # In the case of this MVD we use internal routes for simplicity, but this should not
  # reproduce in prod-grade deployment as all connectors of a dataspace will not be deployed
  # in the same Kubernetes cluster in the real life
  ############################################################################################
  public_url = "${local.scheme}://${local.dataplane_release_name}:8181/api/public/"
  data_plane_image = (
    var.environment == "local" ? "localhost/data-plane-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/data-plane-postgresql-hashicorpvault" :
    var.environment == "selfhosted" ? var.data_plane_image :
    "data-plane-postgresql-hashicorpvault"
  )
}

resource "helm_release" "dataplane" {
  name              = local.dataplane_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = var.charts_path
  chart             = "data-plane"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "dataplane" : {
        "initContainers" : var.tls_enabled ? [
          {
            "name" : "keystore-setup",
            "image" : "${local.data_plane_image}:latest",
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
          "repository" : local.data_plane_image
          "pullPolicy" : local.image_pull_policy
          "tag" : "latest"
        },
        "did" : {
          "web" : {
            "url" : local.did_url,
            "useHttps" : var.tls_enabled
          }
        },
        "keys" : {
          // use the same key pair for simplicity
          "dataplane" : {
            "privateKeyVaultAlias" : local.privatekey_alias,
            "publicKeyVaultAlias" : local.publickey_alias
          }
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
edc.vault.hashicorp.allow.fallback=true
edc.dataplane.state-machine.iteration-wait-millis=${var.data_plane_state_machine_wait_millis}
edc.blobstore.endpoint.template=http://azurite-blobstorage:10000/%s
${local.tls_config_props}
        EOT
        "env" : {
          "JAVA_TOOL_OPTIONS" : local.tls_java_opts
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
              "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.dataplane_release_name}.default.svc.cluster.local"
              "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
              } : {
              "nginx.ingress.kubernetes.io/backend-protocol" : "HTTP"
            }
          ),

          "endpoints" : [
            {
              "port" : 8181,
              "path" : "${var.participant_with_prefix}/dp/(public)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8282,
              "path" : "${var.participant_with_prefix}/dp/(data)(.*)",
              "pathType" : "ImplementationSpecific"
            }
          ]
          "hostname" : "localhost"
          "tls" : {
            "enabled" : var.tls_enabled
            "secretName" : var.tls_enabled ? var.ingress_tls_secret_name : ""
          }
        },

        "selector" : {
          "url" : local.dpf_selector_url
        }

        "url" : {
          "public" : local.public_url
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
                "name" : module.vault.vault_secret_name,
                "tokenKey" : var.selfhosted_vault_token_secret_key != "" ? var.selfhosted_vault_token_secret_key : "rootToken"
              }
            }
          }
        }

      }
    })
  ]

  depends_on = [module.vault, module.db, helm_release.controlplane]
}
