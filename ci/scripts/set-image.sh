#!/usr/bin/env sh
# Sets newName/newTag for one image in a kustomization.yaml `images:` list, in place.
#   ci/scripts/set-image.sh k8s/kustomization.yaml invigilator-api myacr.azurecr.io/invigilator-api 12-abc1234
# Plain awk, so the CI agent needs neither kustomize nor yq.
set -eu
file=$1; name=$2; new_name=$3; new_tag=$4

grep -q "name: ${name}\$" "$file" || { echo "set-image: image '${name}' not found in ${file}" >&2; exit 1; }

tmp=$(mktemp)
awk -v target="$name" -v nn="$new_name" -v nt="$new_tag" '
  /^[[:space:]]*-[[:space:]]+name:[[:space:]]*/ { current = $NF }
  current == target && /^[[:space:]]+newName:/ { sub(/newName:.*/, "newName: " nn) }
  current == target && /^[[:space:]]+newTag:/  { sub(/newTag:.*/,  "newTag: \"" nt "\"") }
  /^[^[:space:]-]/ && !/^images:/ { current = "" }
  { print }
' "$file" > "$tmp"
cat "$tmp" > "$file"
rm -f "$tmp"
echo "set-image: ${name} -> ${new_name}:${new_tag}"
