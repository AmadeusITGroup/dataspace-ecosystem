locals {
  vault_token   = "root"
  vault_port    = 8200
  vault_tls_dir = "/vault/userconfig/vault-tls"
}

########################################
## HASHICORP VAULT + KEY PAIR SEEDING ##
########################################
resource "helm_release" "vault" {
  repository        = "https://helm.releases.hashicorp.com"
  chart             = "vault"
  name              = "${var.participant_name}-vault"
  wait_for_jobs     = true
  version           = "v0.28.1"
  dependency_update = true

  values = [
    yamlencode({
      "injector" : {
        "enabled" : false
      }
      "server" : {
        "dev" : {
          "enabled" : false
        }
        "standalone" : {
          "enabled" : true
          # Use in-memory storage so there is no PVC dependency (same as dev mode).
          # Configure a TLS-enabled TCP listener using the shared internal CA cert.
          "config" : <<-HCL
            ui = true
            listener "tcp" {
              tls_disable     = 0
              address         = "[::]:${local.vault_port}"
              cluster_address = "[::]:8201"
              tls_cert_file   = "${local.vault_tls_dir}/tls.crt"
              tls_key_file    = "${local.vault_tls_dir}/tls.key"
            }
            storage "inmem" {}
          HCL
        }
        # Mount the shared internal TLS secret so vault can use it as its cert.
        "volumes" : [
          {
            "name" : "vault-tls"
            "secret" : {
              "secretName" : var.internal_tls_secret_name
            }
          }
        ]
        "volumeMounts" : [
          {
            "name" : "vault-tls"
            "mountPath" : local.vault_tls_dir
            "readOnly" : true
          }
        ]
        "readinessProbe" : {
          "enabled" : false
        }
      }
    })
  ]
}

#######################
## KUBERNETES SECRET ##
#######################

resource "kubernetes_secret" "vault-secret" {
  metadata {
    name = "${var.participant_name}-vault"
  }

  data = {
    rootToken = local.vault_token
  }
}

####################
### VAULT INGRESS ##
####################

resource "kubernetes_ingress_v1" "vault-ingress" {
  metadata {
    name = "${var.participant_name}-vault-ingress"
    annotations = {
      "nginx.ingress.kubernetes.io/rewrite-target"        = "/$2"
      "nginx.ingress.kubernetes.io/use-regex"             = "true"
      "nginx.ingress.kubernetes.io/ssl-redirect"          = "false"
      "nginx.ingress.kubernetes.io/backend-protocol"      = "HTTPS"
      "nginx.ingress.kubernetes.io/proxy-ssl-verify"      = "on"
      "nginx.ingress.kubernetes.io/proxy-ssl-secret"      = "default/${var.ingress_proxy_ssl_ca_secret_name}"
      "nginx.ingress.kubernetes.io/proxy-ssl-name"        = "${var.participant_name}-vault.default.svc.cluster.local"
      "nginx.ingress.kubernetes.io/proxy-ssl-server-name" = "on"
    }
  }
  spec {
    ingress_class_name = "nginx"
    tls {
      hosts       = ["localhost"]
      secret_name = var.ingress_tls_secret_name
    }
    rule {
      host = "localhost"
      http {
        path {
          path = "/${var.participant_name}/vault(/|$)(.*)"
          backend {
            service {
              name = helm_release.vault.metadata.name
              port {
                number = local.vault_port
              }
            }
          }
        }
      }
    }
  }
}

####################
## VAULT INIT JOB ##
####################
# Initialises vault (standalone mode needs explicit init + unseal),
# enables KV-v2 at secret/, and creates a fixed token with id "root".

resource "kubernetes_job" "vault-init-job" {
  metadata {
    name = "${var.participant_name}-vault-init-job"
  }

  timeouts {
    delete = "15m"
    create = "20m"
    update = "15m"
  }

  spec {
    ttl_seconds_after_finished = 3600
    backoff_limit              = 3
    template {
      metadata {
        name = "${var.participant_name}-vault-init-job"
        labels = {
          app = "${var.participant_name}-vault-init-job"
        }
      }
      spec {
        restart_policy = "Never"
        container {
          name  = "vault-init"
          image = "hashicorp/vault"
          command = [
            "sh", "-c",
            <<-EOF
              set -e

              # Wait for vault to be reachable (any JSON response, even sealed).
              # grep exit code drives the until condition — set -e does not apply here.
              until vault status -format=json 2>/dev/null | grep -q '"initialized"'; do
                echo "Waiting for vault to become reachable..."
                sleep 2
              done

              # Read status; use || true so set -e does not exit on code 2 (sealed).
              STATUS_JSON=$(vault status -format=json 2>/dev/null || true)
              INITIALIZED=$(echo "$STATUS_JSON" | grep -q '"initialized": *true' && echo "true" || echo "false")
              SEALED=$(echo "$STATUS_JSON" | grep -q '"sealed": *true' && echo "true" || echo "false")

              if [ "$INITIALIZED" = "true" ] && [ "$SEALED" = "false" ]; then
                echo "Vault already initialized and unsealed — skipping."
                exit 0
              fi

              if [ "$INITIALIZED" = "false" ]; then
                # Initialize with a single unseal key.
                INIT_JSON=$(vault operator init -key-shares=1 -key-threshold=1 -format=json)
                UNSEAL_KEY=$(echo "$INIT_JSON" | awk '/unseal_keys_b64/{getline; gsub(/[[:space:]",]/, ""); print; exit}')
                INIT_TOKEN=$(echo "$INIT_JSON" | grep '"root_token"' | sed 's/.*"root_token":[[:space:]]*"\([^"]*\)".*/\1/')

                echo "Unsealing vault after init..."
                vault operator unseal "$UNSEAL_KEY"

                echo "Logging in with root token..."
                vault login "$INIT_TOKEN"

                # Enable KV-v2 at secret/ (same as dev mode default).
                vault secrets enable -path=secret kv-v2

                # Create a token with the fixed id so EDC connectors can use it.
                vault token create -id="${local.vault_token}" -policy=root -no-default-policy=true

                echo "Vault initialisation complete."
              else
                echo "ERROR: Vault is initialized but sealed. Manual intervention required."
                exit 1
              fi
            EOF
          ]
          env {
            name  = "VAULT_ADDR"
            value = "https://${var.participant_name}-vault:${local.vault_port}"
          }
          env {
            name  = "VAULT_CACERT"
            value = "/certs/ca.crt"
          }
          volume_mount {
            name       = "vault-tls"
            mount_path = "/certs"
            read_only  = true
          }
        }
        volume {
          name = "vault-tls"
          secret {
            secret_name = var.internal_tls_secret_name
          }
        }
      }
    }
  }
  depends_on          = [helm_release.vault]
  wait_for_completion = true
}

resource "tls_private_key" "key-pair" {
  algorithm   = "ECDSA"
  ecdsa_curve = "P256"
}

###################
## SEED KEY PAIR ##
###################

resource "kubernetes_job" "vault-keygen-job" {
  metadata {
    name = "${var.participant_name}-vault-keygen-job"
    annotations = {
      "helm.sh/hook"               = "post-install,post-upgrade"
      "helm.sh/hook-weight"        = "5"
      "helm.sh/hook-delete-policy" = "hook-succeeded"
    }
  }

  timeouts {
    delete = "15m"
    create = "20m"
    update = "15m"
  }

  spec {
    ttl_seconds_after_finished = 3600
    backoff_limit              = 2
    template {
      metadata {
        name = "${var.participant_name}-vault-keygen-job"
        labels = {
          app = "${var.participant_name}-vault-keygen-job"
        }
      }
      spec {
        restart_policy = "Never"
        container {
          name  = "keygen"
          image = "hashicorp/vault"
          command = [
            "sh",
            "-c",
            <<-EOF
              echo "${tls_private_key.key-pair.public_key_pem}" > /tmp/publickey.pem
              echo "${tls_private_key.key-pair.private_key_pem}" > /tmp/privatekey.pem

              vault kv put secret/${var.participant_name} content="$(cat /tmp/privatekey.pem)"
              vault kv put secret/${var.participant_name}-pub content="$(cat /tmp/publickey.pem)"
            EOF
          ]
          env {
            name  = "VAULT_ADDR"
            value = "https://${var.participant_name}-vault:${local.vault_port}"
          }
          env {
            name  = "VAULT_TOKEN"
            value = local.vault_token
          }
          env {
            name  = "VAULT_CACERT"
            value = "/certs/ca.crt"
          }
          volume_mount {
            name       = "vault-tls"
            mount_path = "/certs"
            read_only  = true
          }
        }
        volume {
          name = "vault-tls"
          secret {
            secret_name = var.internal_tls_secret_name
          }
        }
      }
    }
  }
  depends_on          = [kubernetes_job.vault-init-job]
  wait_for_completion = true
}


