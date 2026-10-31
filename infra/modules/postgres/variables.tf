variable "name" {
  description = "Globally unique server name"
  type        = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "subnet_id" {
  description = "Subnet delegated to Microsoft.DBforPostgreSQL/flexibleServers"
  type        = string
}

variable "private_dns_zone_id" {
  type = string
}

variable "admin_login" {
  type    = string
  default = "invigadmin"
}

variable "admin_password" {
  type      = string
  sensitive = true
}

variable "database_name" {
  type    = string
  default = "invigilator"
}

variable "postgres_version" {
  type    = string
  default = "16"
}

variable "sku_name" {
  description = "B_Standard_B1ms for dev; GP_Standard_D2ds_v5 or larger for prod"
  type        = string
  default     = "B_Standard_B1ms"
}

variable "storage_mb" {
  type    = number
  default = 32768
}

variable "backup_retention_days" {
  type    = number
  default = 7
}

variable "geo_redundant_backup" {
  type    = bool
  default = false
}

variable "high_availability" {
  description = "Zone-redundant standby (not available on Burstable SKUs)"
  type        = bool
  default     = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
