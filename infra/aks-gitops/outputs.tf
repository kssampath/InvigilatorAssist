output "resource_group_name" {
  value = data.azurerm_resource_group.this.name
}

output "acr_name" {
  value = azurerm_container_registry.this.name
}

output "acr_login_server" {
  value = azurerm_container_registry.this.login_server
}

output "acr_admin_username" {
  value = azurerm_container_registry.this.admin_username
}

output "acr_admin_password" {
  value     = azurerm_container_registry.this.admin_password
  sensitive = true
}

output "aks_name" {
  value = azurerm_kubernetes_cluster.this.name
}

output "jenkins_public_ip" {
  value = azurerm_public_ip.jenkins.ip_address
}

output "jenkins_url" {
  value = "http://${azurerm_public_ip.jenkins.ip_address}:8081"
}

output "jenkins_ssh_user" {
  value = "azureuser"
}

output "jenkins_ssh_private_key" {
  description = "SSH key for the Jenkins VM: terraform output -raw jenkins_ssh_private_key > jenkins.pem"
  value       = tls_private_key.jenkins.private_key_openssh
  sensitive   = true
}
