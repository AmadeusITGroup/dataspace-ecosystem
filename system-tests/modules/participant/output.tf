output "did_url" {
  value = local.did_url
}

output "protocol_url" {
  value = local.protocol_url
}

output "kafkaproxy_management_url" {
  value = "http://${local.kafkaproxy_release_name}:8081/management"
}

output "kafkaproxy_api_url" {
  value = "http://${local.kafkaproxy_release_name}:8080/api"
}