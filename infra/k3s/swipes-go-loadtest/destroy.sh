#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" != "--yes" || $# -ne 1 ]]; then
  echo "usage: $0 --yes" >&2
  echo "Deletes only the disposable swipes-go-loadtest namespace." >&2
  exit 64
fi

kubectl delete namespace swipes-go-loadtest --ignore-not-found
