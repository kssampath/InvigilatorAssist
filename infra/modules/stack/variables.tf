variable "project" {
  type    = string
  default = "invig"
}

variable "environment" {
  description = "dev or prod"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod."
  }
}

variable "location" {
  type    = string
  default = "centralindia"
}

variable "tags" {
  type    = map(string)
  default = {}
}

# ---- shared resources created once by infra/bootstrap ----

variable "shared_resource_group_name" {
  type = string
}

variable "acr_name" {
  type = string
}

# ---- what to deploy ----

variable "image_tag" {
  description = "Tag of invigilator-api in the shared ACR; Jenkins passes the git commit SHA"
  type        = string
}

# ---- sizing ----

variable "vnet_address_space" {
  type    = string
  default = "10.40.0.0/16"
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "key_vault_purge_protection" {
  type    = bool
  default = false
}

variable "db_sku_name" {
  type    = string
  default = "B_Standard_B1ms"
}

variable "db_storage_mb" {
  type    = number
  default = 32768
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "db_geo_redundant_backup" {
  type    = bool
  default = false
}

variable "db_high_availability" {
  type    = bool
  default = false
}

variable "static_web_app_location" {
  type    = string
  default = "eastasia"
}

variable "static_web_app_sku" {
  type    = string
  default = "Free"
}

variable "api_cpu" {
  type    = number
  default = 0.5
}

variable "api_memory" {
  type    = string
  default = "1Gi"
}

variable "api_min_replicas" {
  type    = number
  default = 0
}

variable "api_max_replicas" {
  type    = number
  default = 3
}

variable "extra_cors_origins" {
  description = "Custom domains of the web app, e.g. https://exams.college.edu"
  type        = list(string)
  default     = []
}

variable "app_time_zone" {
  type    = string
  default = "Asia/Kolkata"
}
