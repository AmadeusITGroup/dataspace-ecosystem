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

variable "environment" {
  description = "The environment (local or production)"
  type        = string
  default     = "production" # Default to "production" if not defined
}