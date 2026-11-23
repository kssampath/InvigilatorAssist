#!/usr/bin/env bash
# Prints everything the verification screenshots need, each section headed with a timestamp:
# nodes, pods, services, the ArgoCD sync status and the live /api/health response.
#   ./scripts/verify.sh
set -uo pipefail

NAMESPACE=invigilator
section() { printf '\n\033[1m===== %s  [%s] =====\033[0m\n' "$1" "$(date '+%Y-%m-%d %H:%M:%S %Z')"; }

section "kubectl context"
kubectl config current-context

section "Nodes"
kubectl get nodes -o wide

section "Deployments"
kubectl -n "$NAMESPACE" get deployments,statefulsets -o wide

section "Pods"
kubectl -n "$NAMESPACE" get pods -o wide

section "Services"
kubectl -n "$NAMESPACE" get svc -o wide

section "Images running"
kubectl -n "$NAMESPACE" get deploy -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}'

section "ArgoCD application"
kubectl -n argocd get application invigilator-assist \
  -o custom-columns='NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status,REVISION:.status.sync.revision,AUTO-SYNC:.spec.syncPolicy.automated' \
  2>/dev/null || echo "ArgoCD application not found (run scripts/install-argocd.sh)"

IP=$(kubectl -n "$NAMESPACE" get svc invigilator-web -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
section "Application URL"
if [ -n "$IP" ]; then
  echo "Homepage : http://$IP/"
  echo "Health   : http://$IP/api/health"
  section "GET http://$IP/api/health"
  curl -sS --max-time 10 "http://$IP/api/health"; echo
else
  echo "The web LoadBalancer has no external IP yet: kubectl -n $NAMESPACE get svc invigilator-web -w"
fi
