locals {
  controlplane_release_name = "${var.participant_name}-controlplane"

  ##################
  ## IMPORT NOTE! ##
  ############################################################################################
  # These URLs must be the external routes exposed by the participant over the public internet
  # which, are typically exposed through an API gateway, an external Load Balancer...
  # In the case of this MVD we use internal routes for simplicity, but this should not
  # reproduce in prod-grade deployment as all connectors of a dataspace will not be deployed
  # in the same Kubernetes cluster in the real life
  ############################################################################################
  protocol_callback_url = "https://${local.controlplane_release_name}:8282/api/dsp"
  protocol_url          = "${local.protocol_callback_url}/2025-1"

  control_plane_image = (
    var.environment == "local" ? "localhost/control-plane-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/control-plane-postgresql-hashicorpvault" :
    var.environment == "selfhosted" ? var.control_plane_image :
    "control-plane-postgresql-hashicorpvault"
  )
}

resource "helm_release" "controlplane" {
  name              = local.controlplane_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = var.charts_path
  chart             = "control-plane"
  # version           = "latest"

  values = [
    yamlencode({
      "imagePullSecrets" : var.environment == "devbox" ? [
        {
          "name" : var.devbox-registry-cred
        }
      ] : []
      "controlplane" : {
        "initContainers" : [
          {
            "name" : "keystore-setup",
            "image" : "${local.control_plane_image}:latest",
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
          "repository" : local.control_plane_image
          "pullPolicy" : local.image_pull_policy
          "tag" : "latest"
        },
        "sts" : {
          "tokenUrl" : local.sts_url
          "clientId" : local.did_url,
          "clientSecretAlias" : local.sts_client_secret_alias
        }
        "url" : {
          "protocol" : local.protocol_callback_url
        }
        "did" : {
          "web" : {
            "url" : local.did_url
            "useHttps" : true
          }
        },
        "trustedIssuers" : {
          "authority" : {
            "did" : local.authority_did
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

        "config" : <<EOT
edc.iam.trusted-issuer.authority.id=${local.authority_did}
edc.vault.hashicorp.token.scheduled-renew-enabled=false
edc.vault.hashicorp.allow.fallback=true
edc.negotiation.state-machine.iteration-wait-millis=${var.negotiation_state_machine_wait_millis}
edc.transfer.state-machine.iteration-wait-millis=${var.transfer_state_machine_wait_millis}
edc.policy.monitor.state-machine.iteration-wait-millis=${var.policy_monitor_state_machine_wait_millis}
edc.data.plane.selector.state-machine.check.period=5
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
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.controlplane_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
          },
          "endpoints" : [
            {
              "port" : 8181,
              "path" : "${var.participant_with_prefix}/cp/(management)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8282,
              "path" : "${var.participant_with_prefix}/cp/(dsp)(.*)",
              "pathType" : "ImplementationSpecific"
            },
            {
              "port" : 8484,
              "path" : "${var.participant_with_prefix}/cp/(onboarding)(.*)",
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
        },

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
                "tokenKey" : var.selfhosted_vault_token_secret_key != "" ? var.selfhosted_vault_token_secret_key : "rootToken"
              }
            }
          }
        }

      }
    })
  ]

  depends_on = [module.vault, module.db]
}
