output "resource_group_name" {
  value = azurerm_resource_group.this.name
}

output "api_url" {
  value = module.api.url
}

output "api_container_app_name" {
  value = module.api.name
}

output "web_url" {
  value = module.web.url
}

output "web_deployment_token" {
  description = "Used by Jenkins to upload the React build to Static Web Apps"
  value       = module.web.deployment_token
  sensitive   = true
}

output "postgres_fqdn" {
  value = module.postgres.fqdn
}

output "key_vault_uri" {
  value = module.key_vault.uri
}
