# infra/aks-gitops: Terraform alternative to the Azure CLI scripts

The main deployment path uses the Azure CLI scripts (`scripts/azure-provision.sh`, `scripts/jenkins-vm.sh`).
This module creates the same resources with Terraform, for teams that manage Azure as code. All of it goes into
an **existing** resource group (the KodeKloud playground's):

| Resource | Notes |
| --- | --- |
| Azure Container Registry (Basic) | Admin user on: Jenkins pushes with it and the cluster pulls with it, because the playground cannot assign roles. Outside the playground, set `attach_acr_with_role = true` to use the AcrPull role instead. |
| AKS (Free tier) | One node pool, 2 × `Standard_D2s_v3`, Azure CNI overlay, Standard LB, no Container Insights. |
| Jenkins VM | Ubuntu 24.04, `Standard_B2s`, 64 GB Standard_LRS disk, static public IP, NSG for 22 (SSH key only) and 8081 (Jenkins). cloud-init installs Docker, adds 4 GB swap and clones the repo. |

## Run it

Use Azure Cloud Shell or the KodeKloud "Terraform with Azure" terminal, which already have `az` and `terraform`:

```bash
export TF_VAR_resource_group_name=<playground resource group>
bash ci/scripts/tf-backend-init.sh          # creates the state Storage account once, then terraform init
terraform -chdir=infra/aks-gitops plan
terraform -chdir=infra/aks-gitops apply

# then, as with the scripts:
RG=$TF_VAR_resource_group_name
az aks get-credentials -g "$RG" -n "$(terraform -chdir=infra/aks-gitops output -raw aks_name)"
ACR_LOGIN_SERVER=$(terraform -chdir=infra/aks-gitops output -raw acr_login_server) \
ACR_USERNAME=$(terraform -chdir=infra/aks-gitops output -raw acr_admin_username) \
ACR_PASSWORD=$(terraform -chdir=infra/aks-gitops output -raw acr_admin_password) \
  bash scripts/bootstrap-cluster.sh
bash scripts/install-argocd.sh
```

To start Jenkins on the VM, save the key with
`terraform -chdir=infra/aks-gitops output -raw jenkins_ssh_private_key > ~/.ssh/jenkins && chmod 600 ~/.ssh/jenkins`.
Then run `ci/scripts/configure-jenkins-vm.sh` with the environment variables listed in its header.

Remove everything with `terraform -chdir=infra/aks-gitops destroy`.

**Secrets in state:** the ACR admin password and the generated SSH key for the Jenkins VM are in the state file.
That file sits in your own Storage account (private, TLS 1.2, no public blob access). The GitHub token and the
Jenkins password never enter state: they reach the VM over SSH.
