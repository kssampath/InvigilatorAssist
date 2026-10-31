variable "name" {
  type = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "retention_days" {
  type    = number
  default = 30
}

variable "daily_quota_gb" {
  description = "Ingestion cap per day; -1 means no cap"
  type        = number
  default     = -1
}

variable "tags" {
  type    = map(string)
  default = {}
}
