variable "name" {
  type = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "subnet_id" {
  description = "Subnet delegated to Microsoft.App/environments"
  type        = string
}

variable "log_analytics_workspace_id" {
  type = string
}

variable "acr_id" {
  type = string
}

variable "acr_login_server" {
  type = string
}

variable "key_vault_id" {
  type = string
}

variable "image" {
  description = "Full image reference, e.g. myacr.azurecr.io/invigilator-api:<git-sha>"
  type        = string
}

variable "cpu" {
  type    = number
  default = 0.5
}

variable "memory" {
  type    = string
  default = "1Gi"
}

variable "min_replicas" {
  description = "0 lets dev scale to zero; keep at least 1 in prod to avoid cold starts during exams"
  type        = number
  default     = 0
}

variable "max_replicas" {
  type    = number
  default = 3
}

variable "env" {
  description = "Plain environment variables"
  type        = map(string)
  default     = {}
}

variable "key_vault_secrets" {
  description = "Container App secret name => Key Vault versionless secret ID"
  type        = map(string)
  default     = {}
}

variable "secret_env" {
  description = "Environment variable name => Container App secret name"
  type        = map(string)
  default     = {}
}

variable "tags" {
  type    = map(string)
  default = {}
}
