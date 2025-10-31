variable "provider_name" {
  default = "provider"
}

variable "consumer_name" {
  default = "consumer"
}

variable "authority_name" {
  default = "authority"
}

variable "kube_context" {
  default = "kind-eonax-cluster"
}

variable "kube_config_path" {
  default = "~/.kube/config"
}
  
variable "environment" {
  description = "The environment (local, devbox or production)"
  type        = string
  default     = "production" # Default to "production" if not defined
}

variable "devbox-registry" {
  description = "The container registry for devbox environment"
  type        = string
  default     = "cds.sec.io"
}

variable "devbox-registry-cred" {
  description = "The image pull secret name for devbox environment"
  type        = string
  default     = "cdsregistrycred"
}
