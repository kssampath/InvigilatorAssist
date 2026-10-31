# One complete environment of the platform. envs/dev and envs/prod call this with different sizes.
#
#   Static Web App (React) ──HTTPS──► Container App (Spring Boot API) ──private VNet──► PostgreSQL Flexible
#                                      │ pulls image from the shared ACR (managed identity)
#                                      │ reads DB password + JWT secret from Key Vault (managed identity)
#                                      └ logs to Log Analytics

locals {
  name = "${var.project}-${var.environment}"
  tags = merge(var.tags, {
    project     = var.project
    environment = var.environment
    managed_by  = "terraform"
  })
  # Short, lowercase, alphanumeric: used where Azure needs a globally unique name.
  unique = "${replace(var.project, "-", "")}${var.environment}${random_string.suffix.result}"
}

resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "-_"
  min_lower        = 2
  min_upper        = 2
  min_numeric      = 2
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "azurerm_resource_group" "this" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

data "azurerm_container_registry" "shared" {
  name                = var.acr_name
  resource_group_name = var.shared_resource_group_name
}

module "network" {
  source              = "../network"
  name                = local.name
  location            = var.location
  resource_group_name = azurerm_resource_group.this.name
  address_space       = var.vnet_address_space
  tags                = local.tags
}

module "monitoring" {
  source              = "../monitoring"
  name                = local.name
  location            = var.location
  resource_group_name = azurerm_resource_group.this.name
  retention_days      = var.log_retention_days
  tags                = local.tags
}

module "key_vault" {
  source              = "../key_vault"
  name                = "kv-${substr(local.unique, 0, 21)}"
  location            = var.location
  resource_group_name = azurerm_resource_group.this.name
  purge_protection    = var.key_vault_purge_protection
  secret_names        = ["db-password", "jwt-secret"]
  secrets = {
    "db-password" = random_password.db.result
    "jwt-secret"  = random_password.jwt.result
  }
  tags = local.tags
}

module "postgres" {
  source                = "../postgres"
  name                  = "psql-${local.unique}"
  location              = var.location
  resource_group_name   = azurerm_resource_group.this.name
  subnet_id             = module.network.db_subnet_id
  private_dns_zone_id   = module.network.postgres_private_dns_zone_id
  admin_password        = random_password.db.result
  sku_name              = var.db_sku_name
  storage_mb            = var.db_storage_mb
  backup_retention_days = var.db_backup_retention_days
  geo_redundant_backup  = var.db_geo_redundant_backup
  high_availability     = var.db_high_availability
  tags                  = local.tags
}

module "web" {
  source              = "../static_web_app"
  name                = local.name
  location            = var.static_web_app_location
  resource_group_name = azurerm_resource_group.this.name
  sku_tier            = var.static_web_app_sku
  tags                = local.tags
}

module "api" {
  source                     = "../container_app"
  name                       = local.name
  location                   = var.location
  resource_group_name        = azurerm_resource_group.this.name
  subnet_id                  = module.network.apps_subnet_id
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id
  acr_id                     = data.azurerm_container_registry.shared.id
  acr_login_server           = data.azurerm_container_registry.shared.login_server
  key_vault_id               = module.key_vault.id
  image                      = "${data.azurerm_container_registry.shared.login_server}/invigilator-api:${var.image_tag}"
  cpu                        = var.api_cpu
  memory                     = var.api_memory
  min_replicas               = var.api_min_replicas
  max_replicas               = var.api_max_replicas

  env = {
    DB_URL               = module.postgres.jdbc_url
    DB_USERNAME          = module.postgres.admin_login
    CORS_ALLOWED_ORIGINS = join(",", concat([module.web.url], var.extra_cors_origins))
    APP_TIME_ZONE        = var.app_time_zone
    JWT_ISSUER           = "invigilatorassist-${var.environment}"
  }

  key_vault_secrets = {
    "db-password" = module.key_vault.secret_ids["db-password"]
    "jwt-secret"  = module.key_vault.secret_ids["jwt-secret"]
  }

  secret_env = {
    DB_PASSWORD = "db-password"
    JWT_SECRET  = "jwt-secret"
  }

  tags = local.tags
}
