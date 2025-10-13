locals {
  azurite_image = "mcr.microsoft.com/azure-storage/azurite"
}

resource "kubernetes_stateful_set" "azurite" {
  metadata {
    name = "azurite"
  }

  spec {
    service_name = "azurite"
    replicas     = 1

    selector {
      match_labels = {
        app = "azurite"
      }
    }

    template {
      metadata {
        labels = {
          app = "azurite"
        }
      }

      spec {
        container {
          name  = "azurite"
          image = local.azurite_image

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
        }
      }
    }
  }
}

resource "kubernetes_service" "azurite" {
  metadata {
    name = "azurite"
  }

  spec {
    selector = {
      app = "azurite"
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