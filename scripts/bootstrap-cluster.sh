#!/usr/bin/env bash
# Prepares the current kubectl context for InvigilatorAssist: the namespace plus the Secrets that are kept out of
# Git (database password, JWT key, demo admin password, ACR pull credentials). Safe to re-run: existing passwords
# are kept, the pull Secret is refreshed.
#
#   ACR_LOGIN_SERVER=... ACR_USERNAME=... ACR_PASSWORD=... bash scripts/bootstrap-cluster.sh
#
# Used by scripts/azure-provision.sh, and after `terraform apply` when using infra/aks-gitops.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NAMESPACE=invigilator
: "${ACR_LOGIN_SERVER:?set ACR_LOGIN_SERVER}" "${ACR_USERNAME:?set ACR_USERNAME}" "${ACR_PASSWORD:?set ACR_PASSWORD}"

printf '\n\033[1m[%s] Preparing namespace %s and its Secrets\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$NAMESPACE"
kubectl apply -f "$ROOT/k8s/namespace.yaml"

kubectl -n "$NAMESPACE" create secret docker-registry acr-pull \
  --docker-server="$ACR_LOGIN_SERVER" --docker-username="$ACR_USERNAME" --docker-password="$ACR_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

if ! kubectl -n "$NAMESPACE" get secret invigilator-db >/dev/null 2>&1; then
  kubectl -n "$NAMESPACE" create secret generic invigilator-db --from-literal=password="$(openssl rand -hex 16)"
fi
if ! kubectl -n "$NAMESPACE" get secret invigilator-app >/dev/null 2>&1; then
  kubectl -n "$NAMESPACE" create secret generic invigilator-app \
    --from-literal=jwt-secret="$(openssl rand -hex 32)" \
    --from-literal=admin-password="${ADMIN_PASSWORD:-$(openssl rand -hex 6)}"
fi
kubectl -n "$NAMESPACE" get secrets
