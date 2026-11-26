# Screenshots

Evidence for each step of the pipeline, captured on 2026-11-26 (IST) during a run on the KodeKloud
"Terraform with Azure" playground. Every image has a banner with the step, the capture time and its source.

| # | File | What it shows |
| --- | --- | --- |
| 1 | `01-springboot-localhost.png` | Spring Boot API on `localhost:8080` (docker compose on the Jenkins VM): `/api/health` UP, database UP |
| 2 | `02-docker-build.png` | Docker build: API image built and tagged `1-3ad1b6f` (Jenkins build #1) |
| 3 | `03-docker-push-acr.png` | Docker push to Azure Container Registry with digest confirmation |
| 3b | `03b-acr-repositories-tags.png` | `az acr repository list` / `show-tags`: `invigilator-api` and `invigilator-web` at `1-3ad1b6f` and `latest` |
| 4 | `04-jenkins-pipeline-success.png` | Jenkins Stage View: build #1 green through *Update GitOps manifests*; #2 is Jenkins' own `[skip ci]` commit, correctly skipped |
| 4b | `04b-jenkins-console-finished-success.png` | Jenkins console: both images pushed, `Finished: SUCCESS` |
| 5 | `05-kubectl-apply.png` | `kubectl apply -k k8s/` with the image tag written by Jenkins |
| 6 | `06-kubectl-get-pods.png` | `kubectl get pods`: 2 API + 2 web pods (spread over both nodes) + PostgreSQL, all Running |
| 7 | `07-kubectl-get-svc.png` | `kubectl get svc`: `invigilator-web` LoadBalancer with external IP; deployments 2/2 |
| 8 | `08-argocd-synced.png` | ArgoCD: **Healthy**, **Synced** to `master (8886665)`, auto-sync enabled, synced from Jenkins' commit |
| 8b | `08b-argocd-resource-tree.png` | ArgoCD resource tree for `invigilator-assist` |
| 9 | `09-app-homepage.png` | Application homepage in the browser (`http://<EXTERNAL-IP>/`) |
| 10 | `10-api-health.png` | `/api/health` in the browser: status UP, database UP, version `1-3ad1b6f` (= the Jenkins image tag) |
| 11 | `11-gitops-commit.png` | GitHub commit history: `deploy: invigilator 1-3ad1b6f [skip ci]` by Jenkins CI |
| 13 | `13-verify-script.png` | `scripts/verify.sh`: running images, ArgoCD Synced/Healthy with `prune` + `selfHeal`, live health check |
