# Prod: general-purpose DB with zone-redundant HA and geo backups, API always warm.
shared_resource_group_name = "rg-invig-shared"
acr_name                   = "REPLACE_WITH_BOOTSTRAP_OUTPUT"
extra_cors_origins         = [] # e.g. ["https://exams.yourcollege.edu"]

vnet_address_space         = "10.41.0.0/16"
log_retention_days         = 90
key_vault_purge_protection = true

db_sku_name              = "GP_Standard_D2ds_v5"
db_storage_mb            = 65536
db_backup_retention_days = 35
db_geo_redundant_backup  = true
db_high_availability     = true

static_web_app_sku = "Standard"

api_cpu          = 1
api_memory       = "2Gi"
api_min_replicas = 1
api_max_replicas = 6
