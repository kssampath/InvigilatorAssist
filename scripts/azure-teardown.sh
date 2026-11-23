#!/usr/bin/env bash
# Deletes the AKS cluster and the container registry created by azure-provision.sh (the resource group stays).
#   ./scripts/azure-teardown.sh
set -euo pipefail

RESOURCE_GROUP=${RESOURCE_GROUP:-$(az group list --query '[0].name' -o tsv)}
AKS_NAME=${AKS_NAME:-aks-invigilator}
ACR_NAME=${ACR_NAME:-$(az acr list -g "$RESOURCE_GROUP" --query '[0].name' -o tsv)}

echo "This permanently deletes AKS cluster '$AKS_NAME' and registry '${ACR_NAME:-<none>}' in '$RESOURCE_GROUP'."
read -r -p "Type 'delete' to continue: " answer
[ "$answer" = delete ] || { echo "Cancelled."; exit 1; }

az aks delete -g "$RESOURCE_GROUP" -n "$AKS_NAME" --yes --no-wait
[ -n "$ACR_NAME" ] && az acr delete -g "$RESOURCE_GROUP" -n "$ACR_NAME" --yes
echo "Deletion started. Check with: az aks list -g $RESOURCE_GROUP -o table"
