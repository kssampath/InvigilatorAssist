#!/usr/bin/env sh
# Writes the freshly pushed image tags into k8s/kustomization.yaml and pushes that commit to the GitOps branch.
# ArgoCD watches the branch and rolls the new images out to AKS. The commit message carries [skip ci] so the
# pipeline does not rebuild its own commit.
#
# Env: GITOPS_BRANCH, API_IMAGE, WEB_IMAGE, IMAGE_TAG, GIT_SHORT, BUILD_NUMBER, GIT_USER, GIT_TOKEN
set -eu
manifest=k8s/kustomization.yaml

git config user.name  "Jenkins CI"
git config user.email "jenkins@invigilatorassist.local"
# The helper reads the token from the environment at run time; the token itself is never written to disk.
git config credential.helper '!f() { echo "username=${GIT_USER}"; echo "password=${GIT_TOKEN}"; }; f'
trap 'git config --unset credential.helper || true' EXIT

for attempt in 1 2 3; do
  git fetch --no-tags origin "$GITOPS_BRANCH"
  git checkout -q -B "$GITOPS_BRANCH" "origin/$GITOPS_BRANCH"

  ci/scripts/set-image.sh "$manifest" invigilator-api "$API_IMAGE" "$IMAGE_TAG"
  ci/scripts/set-image.sh "$manifest" invigilator-web "$WEB_IMAGE" "$IMAGE_TAG"
  git --no-pager diff -- "$manifest"

  git add "$manifest"
  if git diff --cached --quiet; then
    echo "gitops: manifests already point at ${IMAGE_TAG}"
    exit 0
  fi
  git commit -q -m "deploy: invigilator ${IMAGE_TAG} [skip ci]" \
                -m "Image tag written by Jenkins build #${BUILD_NUMBER} from commit ${GIT_SHORT}."

  if git push origin "HEAD:${GITOPS_BRANCH}"; then
    echo "gitops: pushed $(git rev-parse --short HEAD) to ${GITOPS_BRANCH}; ArgoCD will sync it"
    exit 0
  fi
  echo "gitops: push rejected (branch moved), retrying (${attempt}/3)"
  sleep 3
done
echo "gitops: could not push after 3 attempts" >&2
exit 1
