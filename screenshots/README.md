# Screenshots

Evidence for each step of the pipeline. Save each capture here with the file name below. Show the terminal
prompt, the command and the system clock (or the `[timestamp]` the scripts print), so every image is
labelled and dated.

| # | File | What to capture | How |
| --- | --- | --- | --- |
| 1 | `01-springboot-localhost.png` | Spring Boot app on localhost | `docker compose --profile full up --build`, then open `http://localhost:8080/api/health` (and/or `http://localhost:3000`) |
| 2 | `02-docker-build.png` | Successful image build | README step 3: `docker build ... backend` output ending in `naming to ...invigilator-api:latest`, plus `docker images \| grep invigilator` |
| 3 | `03-docker-push-acr.png` | Push to ACR | `docker push ...` output with the `digest: sha256:...` line, plus `az acr repository list -n <acr> -o table` (or the ACR → Repositories page in the portal) |
| 4 | `04-jenkins-pipeline-success.png` | Full pipeline run | Jenkins job → Stage View with all stages green, and **Console Output** ending in `Finished: SUCCESS` (a second capture `04b-...` of the log is useful) |
| 5 | `05-kubectl-apply.png` | Kubernetes deploy | `kubectl apply -k k8s/` output (`namespace/... configured`, `deployment.apps/... created`, ...) |
| 6 | `06-kubectl-get-pods.png` | 2+ running pods | `kubectl -n invigilator get pods -o wide`: 2 × api, 2 × web, 1 × postgres, all `Running` and `READY 1/1` |
| 7 | `07-kubectl-get-svc.png` | Services | `kubectl -n invigilator get svc`: `invigilator-web` type `LoadBalancer` with an `EXTERNAL-IP` |
| 8 | `08-argocd-synced.png` | GitOps synced | ArgoCD UI: application `invigilator-assist` **Synced** + **Healthy**, auto-sync enabled (App Details → Sync Policy), with the resource tree |
| 9 | `09-app-homepage.png` | App in the browser | `http://<EXTERNAL-IP>/`: the login page, and after logging in as `admin@demo.edu`, the dashboard |
| 10 | `10-api-health.png` | Health endpoint | `http://<EXTERNAL-IP>/api/health` in the browser: `"status":"UP"`, `"database":"UP"`, `version` = Jenkins tag |
| 11 | `11-gitops-commit.png` | (bonus) Jenkins' GitOps commit | GitHub commit list showing `deploy: invigilator N-<sha> [skip ci]` by Jenkins CI |
| 12 | `12-auto-sync-new-version.png` | (bonus) End-to-end | After pushing a code change: ArgoCD shows the new revision, and `/api/health` reports the new `version` |
| 13 | `13-verify-script.png` | (bonus) Summary | `./scripts/verify.sh` output |
