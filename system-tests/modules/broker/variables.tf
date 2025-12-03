variable "environment" {
  description = "The environment (local or production)"
  type        = string
}

variable "devbox-registry" {
  description = "The container registry for devbox environment"
  type        = string
  default     = ""
}

variable "devbox-registry-cred" {
  description = "The image pull secret name for devbox environment"
  type        = string
  default     = ""
}