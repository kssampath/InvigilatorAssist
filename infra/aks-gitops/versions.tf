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
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state in an Azure Storage account in the same resource group. The values are passed at init time
  # (see ci/scripts/tf-backend-init.sh and infra/aks-gitops/README.md), so nothing environment-specific is committed.
  backend "azurerm" {}
}

provider "azurerm" {
  features {}

  # The KodeKloud playground's service principal cannot register resource providers; they are already registered.
  resource_provider_registrations = "none"
}
