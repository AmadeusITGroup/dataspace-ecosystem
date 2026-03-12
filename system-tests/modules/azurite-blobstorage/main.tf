# Azurite Blob Storage module for data plane transfer tests
# This deploys an Azurite instance that can be used for Azure Blob storage transfers
# It uses the default Azurite credentials for simplicity in testing

locals {
  azurite_image   = "mcr.microsoft.com/azure-storage/azurite:${var.azurite_version}"
  azure_cli_image = "mcr.microsoft.com/azure-cli:${var.azure_cli_version}"
  account_name    = var.account_name
  account_key     = var.account_key
}

resource "kubernetes_stateful_set" "azurite_blobstorage" {
  metadata {
    name = "azurite-blobstorage"
  }

  spec {
    service_name = "azurite-blobstorage"
    replicas     = 1

    selector {
      match_labels = {
        app = "azurite-blobstorage"
      }
    }

    template {
      metadata {
        labels = {
          app = "azurite-blobstorage"
        }
      }

      spec {
        container {
          name  = "azurite-blobstorage"
          image = local.azurite_image
          args  = ["azurite", "--skipApiVersionCheck", "--blobHost", "0.0.0.0", "--queueHost", "0.0.0.0", "--tableHost", "0.0.0.0"]

          port {
            container_port = 10000
            name           = "blob"
          }

          port {
            container_port = 10001
            name           = "queue"
          }

          port {
            container_port = 10002
            name           = "table"
          }

          resources {
            requests = {
              cpu    = "100m"
              memory = "128Mi"
            }
            limits = {
              cpu    = "500m"
              memory = "512Mi"
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "azurite_blobstorage" {
  metadata {
    name = "azurite-blobstorage"
  }

  spec {
    selector = {
      app = "azurite-blobstorage"
    }

    port {
      port        = 10000
      target_port = 10000
      protocol    = "TCP"
      name        = "blob"
    }

    port {
      port        = 10001
      target_port = 10001
      protocol    = "TCP"
      name        = "queue"
    }

    port {
      port        = 10002
      target_port = 10002
      protocol    = "TCP"
      name        = "table"
    }
  }
}

# Create test containers for provider and consumer
resource "kubernetes_job" "create_test_containers" {
  metadata {
    name = "create-blobstorage-containers"
  }

  spec {
    backoff_limit = 5

    template {
      metadata {
        labels = {
          app = "create-blobstorage-containers"
        }
      }

      spec {
        restart_policy = "OnFailure"

        container {
          name  = "create-containers"
          image = local.azure_cli_image
          command = [
            "sh", "-c",
            <<-EOT
              echo "Waiting for Azurite service to respond on port 10000..."
              until curl -s http://azurite-blobstorage:10000/${local.account_name}?comp=list >/dev/null 2>&1; do
                echo "Azurite not ready, sleeping 5s..."
                sleep 5
              done

              echo "Azurite is ready. Creating containers..."
              
              # Create provider source container
              az storage container create \
                --name provider-src \
                --account-name ${local.account_name} \
                --account-key ${local.account_key} \
                --connection-string "DefaultEndpointsProtocol=http;AccountName=${local.account_name};AccountKey=${local.account_key};BlobEndpoint=http://azurite-blobstorage:10000/${local.account_name};"
              
              # Create consumer destination container
              az storage container create \
                --name consumer-dest \
                --account-name ${local.account_name} \
                --account-key ${local.account_key} \
                --connection-string "DefaultEndpointsProtocol=http;AccountName=${local.account_name};AccountKey=${local.account_key};BlobEndpoint=http://azurite-blobstorage:10000/${local.account_name};"
              
              # Upload test file to provider source container
              echo '{"message": "Hello from Azure Blob Storage!"}' > /tmp/test-data.json
              az storage blob upload \
                --container-name provider-src \
                --name test-data.json \
                --file /tmp/test-data.json \
                --account-name ${local.account_name} \
                --account-key ${local.account_key} \
                --connection-string "DefaultEndpointsProtocol=http;AccountName=${local.account_name};AccountKey=${local.account_key};BlobEndpoint=http://azurite-blobstorage:10000/${local.account_name};"

              echo "Containers and test data created successfully."
            EOT
          ]
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set.azurite_blobstorage, kubernetes_service.azurite_blobstorage]
}
