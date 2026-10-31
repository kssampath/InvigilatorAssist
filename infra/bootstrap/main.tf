# Run once per subscription, by a person with Owner rights, before any environment:
#   cd infra/bootstrap && terraform init && terraform apply -var deployer_object_id=<Jenkins SP object id>
#
# Creates the things every environment shares:
#   - a storage account holding Terraform state for dev and prod (Azure AD auth only, no access keys)
#   - the container registry Jenkins pushes images to (dev and prod pull the same image tags)
# This root keeps its own state locally; commit nothing from .terraform or *.tfstate.

terraform {
  required_version = ">= 1.9"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.30"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {
  features {}
  storage_use_azuread = true
}

variable "location" {
  type    = string
  default = "centralindia"
}

variable "deployer_object_id" {
  description = "Object ID of the Jenkins service principal (az ad sp show --id <appId> --query id -o tsv)"
  type        = string
}

resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

locals {
  tags = { project = "invig", environment = "shared", managed_by = "terraform" }
}

resource "azurerm_resource_group" "shared" {
  name     = "rg-invig-shared"
  location = var.location
  tags     = local.tags
}

resource "azurerm_storage_account" "tfstate" {
  name                            = "stinvigtfstate${random_string.suffix.result}"
  resource_group_name             = azurerm_resource_group.shared.name
  location                        = var.location
  account_tier                    = "Standard"
  account_replication_type        = "ZRS"
  min_tls_version                 = "TLS1_2"
  shared_access_key_enabled       = false
  allow_nested_items_to_be_public = false
  tags                            = local.tags

  blob_properties {
    versioning_enabled = true # roll back a corrupted state file
    delete_retention_policy {
      days = 30
    }
  }
}

resource "azurerm_storage_container" "tfstate" {
  name                  = "tfstate"
  storage_account_id    = azurerm_storage_account.tfstate.id
  container_access_type = "private"
}

resource "azurerm_container_registry" "shared" {
  name                = "acrinvig${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.shared.name
  location            = var.location
  sku                 = "Standard"
  admin_enabled       = false
  tags                = local.tags
}

# Jenkins: read/write Terraform state and push images.
resource "azurerm_role_assignment" "deployer_state" {
  scope                = azurerm_storage_account.tfstate.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = var.deployer_object_id
}

resource "azurerm_role_assignment" "deployer_push" {
  scope                = azurerm_container_registry.shared.id
  role_definition_name = "AcrPush"
  principal_id         = var.deployer_object_id
}

output "shared_resource_group_name" {
  value = azurerm_resource_group.shared.name
}

output "state_storage_account_name" {
  value = azurerm_storage_account.tfstate.name
}

output "acr_name" {
  value = azurerm_container_registry.shared.name
}

output "acr_login_server" {
  value = azurerm_container_registry.shared.login_server
}
