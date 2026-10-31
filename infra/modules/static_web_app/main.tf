# Hosts the built React app. Jenkins deploys the dist/ folder with the deployment token (api_key).

resource "azurerm_static_web_app" "this" {
  name                = "${var.name}-web"
  location            = var.location
  resource_group_name = var.resource_group_name
  sku_tier            = var.sku_tier
  sku_size            = var.sku_tier
  tags                = var.tags
}
