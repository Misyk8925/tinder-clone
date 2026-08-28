#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: SWIPES_GO_IMAGE=registry.example/swipes-go@sha256:... $0" >&2
  exit 64
fi

if [[ -z "${SWIPES_GO_IMAGE:-}" ]]; then
  echo "SWIPES_GO_IMAGE must be an immutable image reference (prefer @sha256)." >&2
  exit 64
fi

if [[ ! "${SWIPES_GO_IMAGE}" =~ ^[A-Za-z0-9._/@:-]+$ ]]; then
  echo "SWIPES_GO_IMAGE contains unsupported characters." >&2
  exit 64
fi

for command_name in kubectl awk; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "${command_name} is required" >&2
    exit 69
  }
done

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
kubectl create namespace swipes-go-loadtest --dry-run=client -o yaml | kubectl apply -f -

if ! kubectl -n swipes-go-loadtest get secret swipes-loadtest-auth >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Create the isolated benchmark secret first:
  kubectl -n swipes-go-loadtest create secret generic swipes-loadtest-auth \
    --from-literal=internal-auth-secret='<random value>'
EOF
  exit 65
fi

for manifest in namespace.yaml kafka.yaml k6.yaml; do
  kubectl apply -f "${root_dir}/manifests/${manifest}"
done

awk -v image="${SWIPES_GO_IMAGE}" '{ gsub("__SWIPES_GO_IMAGE__", image); print }' \
  "${root_dir}/manifests/swipes-go.yaml" | kubectl apply -f -

kubectl -n swipes-go-loadtest rollout status deployment/swipes-zookeeper --timeout=3m
kubectl -n swipes-go-loadtest rollout status deployment/swipes-kafka --timeout=5m
kubectl -n swipes-go-loadtest rollout status deployment/swipes-go --timeout=5m
kubectl -n swipes-go-loadtest get pods,svc
