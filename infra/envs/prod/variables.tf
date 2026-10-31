variable "location" {
  type    = string
  default = "centralindia"
}

variable "shared_resource_group_name" {
  description = "Created by infra/bootstrap"
  type        = string
}

variable "acr_name" {
  description = "Created by infra/bootstrap"
  type        = string
}

variable "image_tag" {
  description = "Passed by Jenkins: -var image_tag=<git sha>"
  type        = string
}

variable "extra_cors_origins" {
  type    = list(string)
  default = []
}

variable "vnet_address_space" {
  type = string
}

variable "log_retention_days" {
  type = number
}

variable "key_vault_purge_protection" {
  type = bool
}

variable "db_sku_name" {
  type = string
}

variable "db_storage_mb" {
  type = number
}

variable "db_backup_retention_days" {
  type = number
}

variable "db_geo_redundant_backup" {
  type = bool
}

variable "db_high_availability" {
  type = bool
}

variable "static_web_app_sku" {
  type = string
}

variable "api_cpu" {
  type = number
}

variable "api_memory" {
  type = string
}

variable "api_min_replicas" {
  type = number
}

variable "api_max_replicas" {
  type = number
}
