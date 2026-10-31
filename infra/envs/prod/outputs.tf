output "resource_group_name" {
  value = module.stack.resource_group_name
}

output "api_url" {
  value = module.stack.api_url
}

output "api_container_app_name" {
  value = module.stack.api_container_app_name
}

output "web_url" {
  value = module.stack.web_url
}

output "web_deployment_token" {
  value     = module.stack.web_deployment_token
  sensitive = true
}

output "postgres_fqdn" {
  value = module.stack.postgres_fqdn
}
