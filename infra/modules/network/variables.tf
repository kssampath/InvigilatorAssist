variable "name" {
  description = "Name prefix, e.g. invig-dev"
  type        = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "address_space" {
  description = "A /16 for the VNet; the module carves a /23 for apps and a /24 for the database"
  type        = string
  default     = "10.40.0.0/16"

  validation {
    condition     = endswith(var.address_space, "/16")
    error_message = "address_space must be a /16."
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}
