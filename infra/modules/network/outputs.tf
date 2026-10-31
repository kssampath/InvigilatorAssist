output "vnet_id" {
  value = azurerm_virtual_network.this.id
}

output "apps_subnet_id" {
  value = azurerm_subnet.apps.id
}

output "db_subnet_id" {
  value = azurerm_subnet.db.id
}

output "postgres_private_dns_zone_id" {
  description = "Only usable once the VNet link exists, hence the explicit dependency"
  value       = azurerm_private_dns_zone.postgres.id
  depends_on  = [azurerm_private_dns_zone_virtual_network_link.postgres]
}
