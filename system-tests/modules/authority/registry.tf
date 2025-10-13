# locals {
#   server_image               = "nginxinc/nginx-unprivileged:1.25.3"
#   participants_registry_name = "registry"
#   server_port                = 8080
# }
#
# resource "kubernetes_deployment" "registry" {
#   metadata {
#     name = local.participants_registry_name
#     labels = {
#       app = local.participants_registry_name
#     }
#   }
#
#   spec {
#     replicas = 1
#     selector {
#       match_labels = {
#         app = local.participants_registry_name
#       }
#     }
#     template {
#       metadata {
#         labels = {
#           app = local.participants_registry_name
#         }
#       }
#       spec {
#         container {
#           image = local.server_image
#           name  = local.participants_registry_name
#
#           env_from {
#             config_map_ref {
#               name = kubernetes_config_map.registry-config.metadata[0].name
#             }
#           }
#           port {
#             container_port = local.server_port
#             name           = "registry-port"
#           }
#
#           volume_mount {
#             mount_path = "/usr/share/nginx/html"
#             name       = "registry-config"
#           }
#         }
#         volume {
#           name = "registry-config"
#           config_map {
#             name = kubernetes_config_map.registry-config.metadata.0.name
#           }
#         }
#       }
#     }
#   }
# }
#
# resource "kubernetes_config_map" "registry-config" {
#   metadata {
#     name = "participants-registry-config"
#   }
#
#   data = {
#     "participants.json" : jsonencode(var.participants)
#   }
# }
#
# resource "kubernetes_service" "registry-service" {
#   metadata {
#     name = local.participants_registry_name
#   }
#   spec {
#     selector = {
#       app = kubernetes_deployment.registry.spec.0.template.0.metadata[0].labels.app
#     }
#     port {
#       name        = "registry-port"
#       port        = local.server_port
#       target_port = local.server_port
#     }
#   }
# }
#
# resource "kubernetes_ingress_v1" "registry-ingress" {
#   metadata {
#     name = "participants-registry-ingress"
#     annotations = {
#       "nginx.ingress.kubernetes.io/rewrite-target" = "/$2"
#       "nginx.ingress.kubernetes.io/use-regex"      = "true"
#     }
#   }
#   spec {
#     ingress_class_name = "nginx"
#     rule {
#       http {
#         path {
#           path = "/authority/${local.participants_registry_name}(/|$)(.*)"
#           backend {
#             service {
#               name = kubernetes_service.registry-service.metadata.0.name
#               port {
#                 number = local.server_port
#               }
#             }
#           }
#         }
#       }
#     }
#   }
# }
