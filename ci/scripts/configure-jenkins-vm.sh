#!/usr/bin/env bash
# Writes ci/jenkins/.env on the Jenkins VM created by infra/aks-gitops over SSH and starts Jenkins (configured as
# code, see ci/jenkins).
# Secrets travel only over SSH, never through VM metadata or Terraform state. Safe to re-run.
#
# Env: JENKINS_IP, ACR_LOGIN_SERVER, ACR_USERNAME, ACR_PASSWORD, JENKINS_ADMIN_PASSWORD,
#      GITHUB_PUSH_USER, GITHUB_PUSH_TOKEN; SSH key at ~/.ssh/jenkins (from `terraform output jenkins_ssh_private_key`)
set -euo pipefail

for v in JENKINS_IP ACR_LOGIN_SERVER ACR_USERNAME ACR_PASSWORD JENKINS_ADMIN_PASSWORD GITHUB_PUSH_USER GITHUB_PUSH_TOKEN; do
  [ -n "${!v:-}" ] || { echo "configure-jenkins-vm: $v is not set" >&2; exit 1; }
done
REPO_DIR=/opt/invigilatorassist
SSH=(ssh -i "$HOME/.ssh/jenkins" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "azureuser@$JENKINS_IP")

echo "Waiting for SSH on $JENKINS_IP"
for _ in $(seq 1 40); do "${SSH[@]}" true 2>/dev/null && break; sleep 10; done
"${SSH[@]}" "sudo cloud-init status --wait >/dev/null; docker --version; test -d $REPO_DIR"

# The app's public IP, for the pipeline's "Verify deployment" stage (optional; empty if not assigned yet).
APP_IP=""
for _ in $(seq 1 18); do
  APP_IP=$(kubectl -n invigilator get svc invigilator-web -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  [ -n "$APP_IP" ] && break
  sleep 10
done

ENV_TMP=$(mktemp)
trap 'rm -f "$ENV_TMP"' EXIT
cat > "$ENV_TMP" <<ENV
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=$JENKINS_ADMIN_PASSWORD
JENKINS_URL=http://$JENKINS_IP:8081/
JENKINS_PORT=8081
ACR_LOGIN_SERVER=$ACR_LOGIN_SERVER
ACR_USERNAME=$ACR_USERNAME
ACR_PASSWORD=$ACR_PASSWORD
GITHUB_USER=$GITHUB_PUSH_USER
GITHUB_TOKEN=$GITHUB_PUSH_TOKEN
GIT_REPO_URL=https://github.com/kssampath/InvigilatorAssist.git
GIT_BRANCH=master
APP_URL=${APP_IP:+http://$APP_IP}
ENV
"${SSH[@]}" "umask 077 && cat > $REPO_DIR/ci/jenkins/.env" < "$ENV_TMP"

echo "Starting Jenkins (the first start builds its image: about 5 minutes)"
"${SSH[@]}" "cd $REPO_DIR && git pull -q && cd ci/jenkins && sudo DOCKER_GID=\$(getent group docker | cut -d: -f3) bash start.sh"

for _ in $(seq 1 60); do
  code=$("${SSH[@]}" "curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/login" || true)
  [ "$code" = 200 ] && { echo "Jenkins is up: http://$JENKINS_IP:8081"; exit 0; }
  sleep 10
done
echo "Jenkins did not answer within 10 minutes; check: ssh azureuser@$JENKINS_IP 'docker logs invigilator-jenkins'" >&2
exit 1
