#!/usr/bin/env sh
# Polls the deployed app until /api/health answers UP with the expected version, i.e. ArgoCD has synced the
# GitOps commit and Kubernetes has rolled out the new pods.
#   ci/scripts/wait-for-version.sh http://20.1.2.3 12-abc1234 [timeout-seconds]
set -eu
url=${1%/}; expected=$2; timeout=${3:-600}
deadline=$(( $(date +%s) + timeout ))

echo "verify: waiting up to ${timeout}s for ${url}/api/health to report version ${expected}"
while :; do
  body=$(curl -fsS --max-time 5 "${url}/api/health" 2>/dev/null || true)
  case "$body" in
    *"\"version\":\"${expected}\""*'"status":"UP"'*|*'"status":"UP"'*"\"version\":\"${expected}\""*)
      echo "verify: ${body}"
      curl -fsS --max-time 5 -o /dev/null "${url}/" && echo "verify: web homepage OK"
      exit 0 ;;
  esac
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "verify: timed out; last response: ${body:-<none>}" >&2
    exit 1
  fi
  sleep 10
done
