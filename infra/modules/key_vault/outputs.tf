output "id" {
  value = azurerm_key_vault.this.id
}

output "uri" {
  value = azurerm_key_vault.this.vault_uri
}

output "secret_ids" {
  description = "Versionless secret IDs by name, so the app always reads the latest version"
  value       = { for k, s in azurerm_key_vault_secret.this : k => s.versionless_id }
}
