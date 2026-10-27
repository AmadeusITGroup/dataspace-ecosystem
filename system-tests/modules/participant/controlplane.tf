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
  protocol_callback_url = "${local.scheme}://${local.controlplane_release_name}:8282/api/dsp"
  protocol_url          = "${local.protocol_callback_url}/2025-1"

  control_plane_image = (
    var.environment == "local" ? "localhost/control-plane-postgresql-hashicorpvault" :
    var.environment == "devbox" ? "${var.devbox-registry}/control-plane-postgresql-hashicorpvault" :
    var.environment == "selfhosted" ? var.control_plane_image :
    "control-plane-postgresql-hashicorpvault"
  )

  # Category whitelist wiring via the control-plane chart's generic
  # hooks. When disabled, every piece below is empty so the rendered control
  # plane is exactly as without a whitelist (no env, no volume, no mount).
  category_whitelist_env = var.category_whitelist_enabled ? {
    "DSE_CATEGORY_WHITELIST_ENABLED" = "true"
  } : {}
  category_whitelist_volumes = var.category_whitelist_enabled ? [
    {
      "name" = "category-whitelist"
      "configMap" = {
        "name" = var.category_whitelist_configmap
      }
    }
  ] : []
  category_whitelist_volume_mounts = var.category_whitelist_enabled ? [
    {
      "name"      = "category-whitelist"
      "mountPath" = "/etc/dse/category-whitelist"
      "readOnly"  = true
    }
  ] : []
  # Only emit volumes/volumeMounts when THIS module manages the whitelist. When
  # it does not (an umbrella owns them), the keys are absent so the umbrella's
  # own volumes/volumeMounts are not overwritten by empty lists. A for-with-if
  # (not a ternary) keeps the type consistent.
  category_whitelist_hooks = {
    for k, v in {
      volumes      = local.category_whitelist_volumes
      volumeMounts = local.category_whitelist_volume_mounts
    } : k => v if var.category_whitelist_enabled
  }
  # The chart reads the hooks from .Values.controlplane.volumes/volumeMounts, so
  # they are merged into the "controlplane" block, not at the top level.
  controlplane_values_with_hooks = merge(local.controlplane_chart_values, {
    "controlplane" = merge(local.controlplane_chart_values["controlplane"], local.category_whitelist_hooks)
  })
}

resource "helm_release" "controlplane" {
  name              = local.controlplane_release_name
  cleanup_on_fail   = true
  dependency_update = true
  recreate_pods     = true
  repository        = coalesce(var.controlplane_chart_repository, var.charts_path)
  chart             = var.controlplane_chart
  # version           = "latest"

  values = [
    # When controlplane_values_key is set, the deployed chart is an umbrella and
    # the control-plane chart is a subchart under that key, so the values below
    # are nested one level. Empty key = deploy the control-plane chart directly,
    # values at the top level. yamlencode is applied inside each branch so both
    # sides of the conditional are strings (Terraform requires matching types).
    var.controlplane_values_key != "" ?
    yamlencode({ (var.controlplane_values_key) = local.controlplane_values_with_hooks }) :
    yamlencode(local.controlplane_values_with_hooks)
  ]

  depends_on = [module.vault, module.db]
}

locals {
  controlplane_chart_values = {
    "imagePullSecrets" : var.environment == "devbox" ? [
      {
        "name" : var.devbox-registry-cred
      }
    ] : []
    "controlplane" : {
      "initContainers" : var.tls_enabled ? [
        {
          "name" : "keystore-setup",
          "image" : "${local.control_plane_image}:latest",
          "imagePullPolicy" : local.image_pull_policy,
          "command" : ["/bin/sh", "-c"],
          "args" : [
            "cp /etc/pki/ca-trust/extracted/java/cacerts /opt/ca/cacerts && chmod 666 /opt/ca/cacerts && keytool -import -trustcacerts -keystore /opt/ca/cacerts -storepass changeit -noprompt -alias internalCa -file /certs/ca.crt && openssl pkcs12 -export -in /certs/tls.crt -inkey /certs/tls.key -out /opt/ca/keystore.p12 -passout pass:${local.keystore_password} -name service"
          ],
          "volumeMounts" : [
            { "name" : "internal-tls-volume", "mountPath" : "/certs" },
            { "name" : "shared-volume", "mountPath" : "/opt/ca" }
          ]
        }
      ] : [],
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
      "keys" : {
        "encryption" : {
          "aesKeyAlias" : local.aes_key_alias
        }
      }
      "url" : {
        "protocol" : local.protocol_callback_url
      }
      "did" : {
        "web" : {
          "url" : local.did_url
          "useHttps" : var.tls_enabled
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
${local.tls_config_props}
        EOT

      "env" : merge({
        "JAVA_TOOL_OPTIONS" : local.tls_java_opts
        "EDC_IAM_CREDENTIAL_REVOCATION_MIMETYPE" : "application/json"
      }, local.category_whitelist_env)

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
            "nginx.ingress.kubernetes.io/proxy-ssl-name" : "${local.controlplane_release_name}.default.svc.cluster.local"
            "nginx.ingress.kubernetes.io/proxy-ssl-server-name" : "on"
            } : {
            "nginx.ingress.kubernetes.io/backend-protocol" : "HTTP"
          }
        ),
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
        "keystoreSecretName" : var.tls_enabled ? kubernetes_secret.internal-keystore-credentials[0].metadata.0.name : ""
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
  }
}
