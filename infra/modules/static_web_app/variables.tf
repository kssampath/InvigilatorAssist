variable "name" {
  type = string
}

variable "location" {
  description = "Static Web Apps run in a few regions only; eastasia is the closest to India"
  type        = string
  default     = "eastasia"

  validation {
    condition     = contains(["eastasia", "westeurope", "centralus", "eastus2", "westus2"], var.location)
    error_message = "Static Web Apps are available in eastasia, westeurope, centralus, eastus2 and westus2."
  }
}

variable "resource_group_name" {
  type = string
}

variable "sku_tier" {
  description = "Free for dev; Standard for prod (custom domains with SLA, more bandwidth)"
  type        = string
  default     = "Free"
}

variable "tags" {
  type    = map(string)
  default = {}
}
