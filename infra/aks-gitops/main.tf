# Azure Container Registry + AKS for the InvigilatorAssist GitOps deployment.
#
# Sized for the KodeKloud Azure playground policy: existing resource group, one node pool, at most two small
# nodes, no Container Insights (no oms_agent block), and no role assignments unless attach_acr_with_role = true.

data "azurerm_resource_group" "this" {
  name = var.resource_group_name
}

resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
}

locals {
  location = coalesce(var.location, data.azurerm_resource_group.this.location)
  suffix   = random_string.suffix.result
}

# ---- Container registry -----------------------------------------------------------------------------------------
# The admin user gives Jenkins push credentials and the cluster a pull Secret, because the playground cannot
# create role assignments. With attach_acr_with_role = true the cluster pulls with its own identity instead.
resource "azurerm_container_registry" "this" {
  name                = "acr${var.name_prefix}${local.suffix}"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  sku                 = "Basic"
  admin_enabled       = true
  tags                = var.tags
}

# ---- AKS ---------------------------------------------------------------------------------------------------------
resource "azurerm_kubernetes_cluster" "this" {
  name                = "aks-${var.name_prefix}-${local.suffix}"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  dns_prefix          = "aks-${var.name_prefix}-${local.suffix}"
  kubernetes_version  = var.kubernetes_version
  sku_tier            = "Free"
  tags                = var.tags

  default_node_pool {
    name                        = "system"
    vm_size                     = var.aks_node_vm_size
    node_count                  = var.aks_node_count
    os_disk_size_gb             = 64
    temporary_name_for_rotation = "tmpsystem"
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    load_balancer_sku   = "standard"
  }

  lifecycle {
    ignore_changes = [default_node_pool[0].upgrade_settings]
  }
}

resource "azurerm_role_assignment" "aks_acr_pull" {
  count = var.attach_acr_with_role ? 1 : 0

  scope                            = azurerm_container_registry.this.id
  role_definition_name             = "AcrPull"
  principal_id                     = azurerm_kubernetes_cluster.this.kubelet_identity[0].object_id
  skip_service_principal_aad_check = true
}
