#!/usr/bin/env bash
# Creates the Azure side of the pipeline: an Azure Container Registry and a 2-node AKS cluster, then prepares the
# cluster (namespace, Secrets, ACR pull access) and writes the ACR credentials into ci/jenkins/.env for Jenkins.
#
# Sized for the KodeKloud Azure playground policy: use the pre-created resource group, one node pool, at most
# 2 nodes, a small allowed VM size, no Container Insights. Re-running is safe: existing resources are reused and
# existing passwords are kept.
#
#   az login                         # with the playground account
#   ./scripts/azure-provision.sh
#
# Optional overrides: RESOURCE_GROUP, LOCATION, ACR_NAME, AKS_NAME, NODE_COUNT, NODE_SIZE (space-separated list
# tried in order), ADMIN_PASSWORD (demo admin login).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NAMESPACE=invigilator
log()  { printf '\n\033[1m[%s] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

for tool in az kubectl openssl; do
  command -v "$tool" >/dev/null || fail "$tool is not installed"
done
az account show -o none 2>/dev/null || fail "not logged in to Azure: run 'az login' first"

RESOURCE_GROUP=${RESOURCE_GROUP:-$(az group list --query '[0].name' -o tsv)}
[ -n "$RESOURCE_GROUP" ] || fail "no resource group found; set RESOURCE_GROUP"
LOCATION=${LOCATION:-$(az group show -n "$RESOURCE_GROUP" --query location -o tsv)}
AKS_NAME=${AKS_NAME:-aks-invigilator}
NODE_COUNT=${NODE_COUNT:-2}
NODE_SIZES=${NODE_SIZE:-"Standard_D2s_v3 Standard_B2s Standard_K8S2_v1 Standard_D4s_v3"}

log "Subscription: $(az account show --query name -o tsv) | resource group: $RESOURCE_GROUP | location: $LOCATION"

# ---- Azure Container Registry -------------------------------------------------------------------------------
ACR_NAME=${ACR_NAME:-$(az acr list -g "$RESOURCE_GROUP" --query '[0].name' -o tsv)}
if [ -z "$ACR_NAME" ]; then
  ACR_NAME="acrinvig$(openssl rand -hex 4)"
  log "Creating container registry $ACR_NAME"
  az acr create -g "$RESOURCE_GROUP" -n "$ACR_NAME" -l "$LOCATION" --sku Basic --admin-enabled true -o none \
    || az acr create -g "$RESOURCE_GROUP" -n "$ACR_NAME" -l "$LOCATION" --sku Standard --admin-enabled true -o none
else
  log "Using existing container registry $ACR_NAME"
  az acr update -n "$ACR_NAME" --admin-enabled true -o none
fi
ACR_LOGIN_SERVER=$(az acr show -n "$ACR_NAME" --query loginServer -o tsv)
ACR_USERNAME=$(az acr credential show -n "$ACR_NAME" --query username -o tsv)
ACR_PASSWORD=$(az acr credential show -n "$ACR_NAME" --query 'passwords[0].value' -o tsv)

# ---- AKS -----------------------------------------------------------------------------------------------------
if az aks show -g "$RESOURCE_GROUP" -n "$AKS_NAME" -o none 2>/dev/null; then
  log "Using existing AKS cluster $AKS_NAME"
else
  created=false
  for size in $NODE_SIZES; do
    log "Creating AKS cluster $AKS_NAME: $NODE_COUNT x $size (takes 5-10 minutes)"
    if az aks create -g "$RESOURCE_GROUP" -n "$AKS_NAME" -l "$LOCATION" \
         --node-count "$NODE_COUNT" --node-vm-size "$size" \
         --tier free --no-ssh-key -o none; then
      created=true
      break
    fi
    log "VM size $size was not accepted; trying the next one"
  done
  $created || fail "could not create AKS with any of: $NODE_SIZES (set NODE_SIZE to a size your subscription allows)"
fi

az aks get-credentials -g "$RESOURCE_GROUP" -n "$AKS_NAME" --overwrite-existing -o none
kubectl get nodes -o wide

log "Granting AKS pull access to $ACR_NAME"
if az aks update -g "$RESOURCE_GROUP" -n "$AKS_NAME" --attach-acr "$ACR_NAME" -o none 2>/dev/null; then
  echo "AKS kubelet identity attached to ACR (AcrPull role)."
else
  echo "Could not assign the AcrPull role (normal on the KodeKloud playground); using the acr-pull Secret instead."
fi

# ---- Namespace and Secrets (kept out of Git) ---------------------------------------------------------------
log "Preparing namespace $NAMESPACE and its Secrets"
kubectl apply -f "$ROOT/k8s/namespace.yaml"

kubectl -n "$NAMESPACE" create secret docker-registry acr-pull \
  --docker-server="$ACR_LOGIN_SERVER" --docker-username="$ACR_USERNAME" --docker-password="$ACR_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

if ! kubectl -n "$NAMESPACE" get secret invigilator-db >/dev/null 2>&1; then
  kubectl -n "$NAMESPACE" create secret generic invigilator-db --from-literal=password="$(openssl rand -hex 16)"
fi
if ! kubectl -n "$NAMESPACE" get secret invigilator-app >/dev/null 2>&1; then
  ADMIN_PASSWORD=${ADMIN_PASSWORD:-$(openssl rand -hex 6)}
  kubectl -n "$NAMESPACE" create secret generic invigilator-app \
    --from-literal=jwt-secret="$(openssl rand -hex 32)" \
    --from-literal=admin-password="$ADMIN_PASSWORD"
fi
kubectl -n "$NAMESPACE" get secrets

# ---- Jenkins settings ------------------------------------------------------------------------------------------
ENV_FILE="$ROOT/ci/jenkins/.env"
[ -f "$ENV_FILE" ] || cp "$ROOT/ci/jenkins/.env.example" "$ENV_FILE"
set_env() {
  local tmp
  tmp=$(mktemp)
  awk -v k="$1" -v v="$2" '
    index($0, k "=") == 1 { print k "=" v; done = 1; next }
    { print }
    END { if (!done) print k "=" v }
  ' "$ENV_FILE" > "$tmp"
  cat "$tmp" > "$ENV_FILE"
  rm -f "$tmp"
}
set_env ACR_LOGIN_SERVER "$ACR_LOGIN_SERVER"
set_env ACR_USERNAME "$ACR_USERNAME"
set_env ACR_PASSWORD "$ACR_PASSWORD"
chmod 600 "$ENV_FILE"

# ---- Point the manifests at this registry -------------------------------------------------------------------
if ! grep -q "newName: $ACR_LOGIN_SERVER/invigilator-api" "$ROOT/k8s/kustomization.yaml"; then
  "$ROOT/ci/scripts/set-image.sh" "$ROOT/k8s/kustomization.yaml" invigilator-api "$ACR_LOGIN_SERVER/invigilator-api" latest
  "$ROOT/ci/scripts/set-image.sh" "$ROOT/k8s/kustomization.yaml" invigilator-web "$ACR_LOGIN_SERVER/invigilator-web" latest
  MANIFEST_CHANGED=true
fi

log "Done"
cat <<EOF
  Resource group   : $RESOURCE_GROUP
  AKS cluster      : $AKS_NAME   (kubectl context: $(kubectl config current-context))
  Registry         : $ACR_LOGIN_SERVER
  Jenkins settings : ci/jenkins/.env (ACR_* written; add GITHUB_USER / GITHUB_TOKEN / JENKINS_ADMIN_PASSWORD)
  Demo admin login : admin@demo.edu  /  password:
                     kubectl -n $NAMESPACE get secret invigilator-app -o jsonpath='{.data.admin-password}' | base64 -d; echo
EOF
if [ "${MANIFEST_CHANGED:-false}" = true ]; then
  cat <<'EOF'

  k8s/kustomization.yaml now points at this registry. Commit and push it so ArgoCD uses the same registry:
    git add k8s/kustomization.yaml && git commit -m "k8s: use this environment's ACR" && git push
EOF
fi
