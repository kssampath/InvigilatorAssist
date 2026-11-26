#!/usr/bin/env bash
# Runs the Jenkins build server (and Docker) on a small Ubuntu VM in Azure, so nothing heavy runs on your laptop.
# Run it from Azure Cloud Shell (Bash) or any machine with az, ssh and scp, after scripts/azure-provision.sh:
#
#   bash scripts/jenkins-vm.sh
#
# Before running, ci/jenkins/.env must hold ACR_* (written by azure-provision.sh), JENKINS_ADMIN_PASSWORD,
# GITHUB_USER and GITHUB_TOKEN. The script creates the VM (Docker installed by cloud-init, 4 GB swap for the
# Maven/npm builds, Standard HDD disk because the KodeKloud policy rejects Premium disks), copies .env over SSH (secrets never go into VM metadata), starts Jenkins on port 8081 and
# prints its URL.
#
# Optional: VM_NAME, VM_SIZE (space-separated list tried in order), ALLOW_FROM (source IP/CIDR allowed to reach
# Jenkins; default: Internet, protected by the Jenkins login), GIT_BRANCH (default: master).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
log()  { printf '\n\033[1m[%s] %s\033[0m\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

for tool in az ssh scp; do command -v "$tool" >/dev/null || fail "$tool is not installed"; done
az account show -o none 2>/dev/null || fail "not logged in to Azure: run 'az login' first"

ENV_FILE="$ROOT/ci/jenkins/.env"
[ -f "$ENV_FILE" ] || fail "$ENV_FILE not found: run scripts/azure-provision.sh first"
for key in ACR_LOGIN_SERVER ACR_USERNAME ACR_PASSWORD JENKINS_ADMIN_PASSWORD GITHUB_USER GITHUB_TOKEN; do
  grep -Eq "^${key}=.+" "$ENV_FILE" || fail "$key is empty in ci/jenkins/.env"
done

RESOURCE_GROUP=${RESOURCE_GROUP:-$(az group list --query '[0].name' -o tsv)}
LOCATION=${LOCATION:-$(az group show -n "$RESOURCE_GROUP" --query location -o tsv)}
VM_NAME=${VM_NAME:-vm-jenkins}
VM_SIZES=${VM_SIZE:-"Standard_D2s_v3 Standard_B2s"}
ALLOW_FROM=${ALLOW_FROM:-Internet}
GIT_BRANCH=${GIT_BRANCH:-master}
REPO_URL=${GIT_REPO_URL:-https://github.com/kssampath/InvigilatorAssist.git}
ADMIN_USER=azureuser
REMOTE_DIR=/opt/invigilatorassist

CLOUD_INIT=$(mktemp)
trap 'rm -f "$CLOUD_INIT"' EXIT
cat > "$CLOUD_INIT" <<EOF
#cloud-config
package_update: true
packages: [ca-certificates, curl, git]
runcmd:
  - fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  - echo '/swapfile none swap sw 0 0' >> /etc/fstab
  - curl -fsSL https://get.docker.com | sh
  - usermod -aG docker ${ADMIN_USER}
  - git clone --branch ${GIT_BRANCH} ${REPO_URL} ${REMOTE_DIR}
  - chown -R ${ADMIN_USER}:${ADMIN_USER} ${REMOTE_DIR}
EOF

if az vm show -g "$RESOURCE_GROUP" -n "$VM_NAME" -o none 2>/dev/null; then
  log "Using existing VM $VM_NAME"
else
  created=false
  for size in $VM_SIZES; do
    log "Creating VM $VM_NAME ($size, Ubuntu 24.04) in $RESOURCE_GROUP"
    if az vm create -g "$RESOURCE_GROUP" -n "$VM_NAME" -l "$LOCATION" \
         --image Ubuntu2404 --size "$size" --admin-username "$ADMIN_USER" \
         --generate-ssh-keys --public-ip-sku Standard --os-disk-size-gb 64 --storage-sku Standard_LRS \
         --custom-data "$CLOUD_INIT" -o none; then
      created=true
      break
    fi
    log "VM size $size was not accepted; trying the next one"
  done
  $created || fail "could not create the VM with any of: $VM_SIZES (set VM_SIZE)"

  log "Opening port 8081 (Jenkins) to $ALLOW_FROM"
  NSG=$(az network nsg list -g "$RESOURCE_GROUP" --query "[?starts_with(name, '${VM_NAME}')].name | [0]" -o tsv)
  az network nsg rule create -g "$RESOURCE_GROUP" --nsg-name "$NSG" -n allow-jenkins-8081 \
    --priority 1010 --access Allow --protocol Tcp --direction Inbound \
    --source-address-prefixes "$ALLOW_FROM" --destination-port-ranges 8081 -o none
fi

IP=$(az vm show -d -g "$RESOURCE_GROUP" -n "$VM_NAME" --query publicIps -o tsv)
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10)

log "Waiting for the VM to finish installing Docker (a few minutes on first boot)"
for _ in $(seq 1 30); do
  ssh "${SSH_OPTS[@]}" "$ADMIN_USER@$IP" true 2>/dev/null && break
  sleep 10
done
ssh "${SSH_OPTS[@]}" "$ADMIN_USER@$IP" 'sudo cloud-init status --wait >/dev/null; docker --version'

log "Copying Jenkins settings and starting Jenkins"
TMP_ENV=$(mktemp)
grep -v '^JENKINS_URL=' "$ENV_FILE" > "$TMP_ENV"
echo "JENKINS_URL=http://$IP:8081/" >> "$TMP_ENV"
scp "${SSH_OPTS[@]}" "$TMP_ENV" "$ADMIN_USER@$IP:$REMOTE_DIR/ci/jenkins/.env"
rm -f "$TMP_ENV"
ssh "${SSH_OPTS[@]}" "$ADMIN_USER@$IP" "cd $REMOTE_DIR && git pull -q && chmod 600 ci/jenkins/.env && \
  cd ci/jenkins && sudo DOCKER_GID=\$(getent group docker | cut -d: -f3) bash start.sh"

log "Jenkins is starting (first start builds its image: about 5 minutes)"
cat <<EOF
  Jenkins UI : http://$IP:8081   (user: admin, password: JENKINS_ADMIN_PASSWORD from ci/jenkins/.env)
  SSH        : ssh $ADMIN_USER@$IP
  Logs       : ssh $ADMIN_USER@$IP 'docker logs -f invigilator-jenkins'

  Local-run screenshot (Spring Boot on localhost:8080), on the VM:
    ssh $ADMIN_USER@$IP
    cd $REMOTE_DIR && docker compose --profile full up -d --build && sleep 60 && curl -s localhost:8080/api/health
    docker compose --profile full down     # free the memory for Jenkins afterwards
EOF
