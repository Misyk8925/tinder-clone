#!/usr/bin/env bash
set -Eeuo pipefail

namespace="swipes-go-loadtest"
rate="${LOAD_RATE:-2000}"
duration="${LOAD_DURATION:-30s}"
pre_allocated_vus="${LOAD_PRE_ALLOCATED_VUS:-500}"
max_vus="${LOAD_MAX_VUS:-2000}"
p95_ms="${LOAD_P95_MS:-250}"

for command_name in kubectl date; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "${command_name} is required" >&2
    exit 69
  }
done

kubectl -n "${namespace}" rollout status deployment/swipes-go --timeout=30s >/dev/null
job_name="swipes-k6-$(date +%Y%m%d%H%M%S)"
kubectl -n "${namespace}" create job "${job_name}" --from=cronjob/swipes-k6 --dry-run=client -o yaml | \
  kubectl set env --local -f - -o yaml \
  "LOAD_RATE=${rate}" \
  "LOAD_DURATION=${duration}" \
  "LOAD_PRE_ALLOCATED_VUS=${pre_allocated_vus}" \
  "LOAD_MAX_VUS=${max_vus}" \
  "LOAD_P95_MS=${p95_ms}" | kubectl apply -f -

if ! kubectl -n "${namespace}" wait --for=condition=complete "job/${job_name}" --timeout=10m; then
  kubectl -n "${namespace}" logs "job/${job_name}" || true
  exit 1
fi

kubectl -n "${namespace}" logs "job/${job_name}"
kubectl -n "${namespace}" get pods -l app.kubernetes.io/name=swipes-go
