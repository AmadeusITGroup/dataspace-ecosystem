locals {
  backend_service_image = (
    var.environment == "local" ? "localhost/backend-service-provider:latest" : "backend-service-provider:latest"
  )
  port = 8080
}

resource "kubernetes_deployment" "backend" {
  metadata {
    name = "${var.name}-backend"
    labels = {
      app = "${var.name}-backend"
    }
  }

  spec {
    replicas = 1
    selector {
      match_labels = {
        app = "${var.name}-backend"
      }
    }
    template {
      metadata {
        labels = {
          app = "${var.name}-backend"
        }
      }
      spec {
        container {
          image             = local.backend_service_image
          name              = "${var.name}-backend"
          image_pull_policy = "IfNotPresent"

          port {
            container_port = local.port
            name           = "backend-port"
          }

          liveness_probe {
            http_get {
              port   = "8080"
              path   = "/api/check/liveness"
              scheme = "HTTP"
            }
            failure_threshold = 10
            period_seconds    = 5
            timeout_seconds   = 30
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "backend-provider-svc" {
  metadata {
    name = "${var.name}-backend"
  }
  spec {
    selector = {
      app = kubernetes_deployment.backend.spec.0.template.0.metadata[0].labels.app
    }
    port {
      name        = "backend-svc-port"
      port        = local.port
      target_port = local.port
    }
  }
}
