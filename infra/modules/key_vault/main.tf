# Key Vault (RBAC mode) holding the app's secrets. The identity running Terraform gets
# "Key Vault Secrets Officer" so it can write them; the app gets read access in the container_app module.

data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "this" {
  name                       = var.name
  location                   = var.location
  resource_group_name        = var.resource_group_name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  enable_rbac_authorization  = true
  purge_protection_enabled   = var.purge_protection
  soft_delete_retention_days = 7
  tags                       = var.tags
}

resource "azurerm_role_assignment" "deployer" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Role assignments take a little while to apply; writing secrets straight away would get a 403.
resource "time_sleep" "rbac" {
  depends_on      = [azurerm_role_assignment.deployer]
  create_duration = "60s"
}

resource "azurerm_key_vault_secret" "this" {
  for_each     = toset(var.secret_names)
  name         = each.key
  value        = var.secrets[each.key]
  key_vault_id = azurerm_key_vault.this.id
  content_type = "text/plain"
  depends_on   = [time_sleep.rbac]
}
