# Dev: smallest sizes, scales to zero when idle.
shared_resource_group_name = "rg-invig-shared"
acr_name                   = "REPLACE_WITH_BOOTSTRAP_OUTPUT"

vnet_address_space         = "10.40.0.0/16"
log_retention_days         = 30
key_vault_purge_protection = false

db_sku_name              = "B_Standard_B1ms"
db_storage_mb            = 32768
db_backup_retention_days = 7
db_geo_redundant_backup  = false
db_high_availability     = false

static_web_app_sku = "Free"

api_cpu          = 0.5
api_memory       = "1Gi"
api_min_replicas = 0
api_max_replicas = 2
