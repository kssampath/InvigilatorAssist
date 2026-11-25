#!/usr/bin/env bash
# Starts Jenkins with the Docker socket group detected for this host.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { cp .env.example .env; echo "Created ci/jenkins/.env from the example: fill it in, then re-run."; exit 1; }

if [ -z "${DOCKER_GID:-}" ]; then
  # Linux: group of /var/run/docker.sock. Docker Desktop (macOS/Windows): the socket inside the VM is root's.
  DOCKER_GID=$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo 0)
  [ "$(uname -s)" = "Darwin" ] && DOCKER_GID=0
fi
export DOCKER_GID

docker compose up -d --build
echo "Jenkins is starting on http://localhost:${JENKINS_PORT:-8081} (log in with JENKINS_ADMIN_ID / JENKINS_ADMIN_PASSWORD from .env)"
echo "Follow the log with: docker logs -f invigilator-jenkins"
