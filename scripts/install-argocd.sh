#!/usr/bin/env bash
# Installs ArgoCD on the current kubectl context and registers the InvigilatorAssist Application (auto-sync on).
#   bash scripts/install-argocd.sh
# Optional: ARGOCD_VERSION (default: stable), ARGOCD_MINIMAL=false to keep Dex/notifications/ApplicationSet running,
#           ARGOCD_EXPOSE=port-forward to keep the UI private instead of giving it a public IP.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
ARGOCD_VERSION=${ARGOCD_VERSION:-stable}
ARGOCD_MINIMAL=${ARGOCD_MINIMAL:-true}
ARGOCD_EXPOSE=${ARGOCD_EXPOSE:-loadbalancer}
log() { printf '\n\033[1m[%s] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

log "Installing ArgoCD ($ARGOCD_VERSION) into namespace argocd"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd --server-side --force-conflicts \
  -f "https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

if [ "$ARGOCD_MINIMAL" = true ]; then
  # Not needed here (local admin login, no notifications, no ApplicationSets); frees memory on 2 small nodes.
  kubectl -n argocd scale deploy argocd-dex-server argocd-notifications-controller \
    argocd-applicationset-controller --replicas=0
fi

log "Waiting for ArgoCD to be ready"
kubectl -n argocd rollout status deploy/argocd-server --timeout=5m
kubectl -n argocd rollout status deploy/argocd-repo-server --timeout=5m
kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=5m

log "Registering the InvigilatorAssist project and Application"
kubectl apply -f "$ROOT/argocd/project.yaml"
kubectl apply -f "$ROOT/argocd/application.yaml"
kubectl -n argocd get applications

if [ "$ARGOCD_EXPOSE" = loadbalancer ]; then
  kubectl -n argocd patch svc argocd-server -p '{"spec":{"type":"LoadBalancer"}}' >/dev/null
  log "Waiting for the ArgoCD UI's public IP"
  for _ in $(seq 1 60); do
    ip=$(kubectl -n argocd get svc argocd-server -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
    [ -n "$ip" ] && break
    sleep 5
  done
  UI="https://${ip:-<pending: kubectl -n argocd get svc argocd-server>}"
else
  UI="https://localhost:8443   (run: kubectl -n argocd port-forward svc/argocd-server 8443:443)"
fi

log "ArgoCD is ready"
cat <<EOF
  UI       : $UI   (self-signed certificate: accept the browser warning)
  User     : admin
  Password : kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
  Status   : kubectl -n argocd get application invigilator-assist
EOF
