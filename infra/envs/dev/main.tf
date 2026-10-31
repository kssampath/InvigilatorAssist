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
    time = {
      source  = "hashicorp/time"
      version = "~> 0.12"
    }
  }

  # Remote state in the storage account created by infra/bootstrap. The values come from
  # backend.hcl (see backend.hcl.example) so the same code works for any subscription:
  #   terraform init -backend-config=backend.hcl
  backend "azurerm" {
    key = "invigilator/dev.tfstate"
  }
}

# Subscription and credentials come from ARM_* environment variables (Jenkins injects a service principal).
provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy = true
    }
  }
}

module "stack" {
  source = "../../modules/stack"

  environment                = "dev"
  location                   = var.location
  shared_resource_group_name = var.shared_resource_group_name
  acr_name                   = var.acr_name
  image_tag                  = var.image_tag
  extra_cors_origins         = var.extra_cors_origins

  vnet_address_space         = var.vnet_address_space
  log_retention_days         = var.log_retention_days
  key_vault_purge_protection = var.key_vault_purge_protection
  db_sku_name                = var.db_sku_name
  db_storage_mb              = var.db_storage_mb
  db_backup_retention_days   = var.db_backup_retention_days
  db_geo_redundant_backup    = var.db_geo_redundant_backup
  db_high_availability       = var.db_high_availability
  static_web_app_sku         = var.static_web_app_sku
  api_cpu                    = var.api_cpu
  api_memory                 = var.api_memory
  api_min_replicas           = var.api_min_replicas
  api_max_replicas           = var.api_max_replicas
}
