output "fqdn" {
  value = azurerm_container_app.api.ingress[0].fqdn
}

output "url" {
  value = "https://${azurerm_container_app.api.ingress[0].fqdn}"
}

output "name" {
  value = azurerm_container_app.api.name
}

output "identity_principal_id" {
  value = azurerm_user_assigned_identity.app.principal_id
}
