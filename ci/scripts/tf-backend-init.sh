#!/usr/bin/env bash
# Creates (once) the Storage account that holds Terraform state for infra/aks-gitops, then runs terraform init
# against it. Its name is derived from the subscription and resource group, so every run finds the same one.
# Needs: az logged in, TF_DIR, TF_VAR_resource_group_name, ARM_SUBSCRIPTION_ID (or the current az subscription).
set -euo pipefail

TF_DIR=${TF_DIR:-infra/aks-gitops}
RG=${TF_VAR_resource_group_name:?set TF_VAR_resource_group_name to the resource group}
SUB=${ARM_SUBSCRIPTION_ID:-$(az account show --query id -o tsv)}
SA="tfinvig$(printf '%s' "$SUB$RG" | sha1sum | cut -c1-12)"

if ! az storage account show -n "$SA" -g "$RG" -o none 2>/dev/null; then
  echo "Creating Terraform state storage account $SA in $RG"
  az storage account create -n "$SA" -g "$RG" --sku Standard_LRS --kind StorageV2 \
    --min-tls-version TLS1_2 --allow-blob-public-access false -o none
fi
KEY=$(az storage account keys list -n "$SA" -g "$RG" --query '[0].value' -o tsv)
[ -n "${GITHUB_ACTIONS:-}" ] && echo "::add-mask::$KEY"
az storage container create -n tfstate --account-name "$SA" --account-key "$KEY" -o none

# The azurerm backend reads the key from ARM_ACCESS_KEY; keep it for later steps of the same job.
export ARM_ACCESS_KEY="$KEY"
[ -n "${GITHUB_ENV:-}" ] && echo "ARM_ACCESS_KEY=$KEY" >> "$GITHUB_ENV"

terraform -chdir="$TF_DIR" init -input=false \
  -backend-config="resource_group_name=$RG" \
  -backend-config="storage_account_name=$SA" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=aks-gitops.tfstate"
