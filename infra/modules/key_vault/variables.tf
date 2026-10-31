variable "name" {
  description = "Globally unique, 3-24 characters"
  type        = string

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-]{1,22}[a-zA-Z0-9]$", var.name))
    error_message = "Key Vault names are 3-24 letters, digits and hyphens, starting with a letter."
  }
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "secret_names" {
  description = "Names of the secrets to create (kept separate from the values so for_each keys are not sensitive)"
  type        = list(string)
}

variable "secrets" {
  description = "Secret values keyed by name"
  type        = map(string)
  sensitive   = true
}

variable "purge_protection" {
  description = "Recommended in prod; once on it cannot be turned off and the vault name stays reserved for 7 days after delete"
  type        = bool
  default     = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
