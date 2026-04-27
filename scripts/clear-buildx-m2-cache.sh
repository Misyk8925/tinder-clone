#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/clear-buildx-m2-cache.sh <cache-id-or-all> [builder]

Examples:
  ./scripts/clear-buildx-m2-cache.sh m2-profiles
  ./scripts/clear-buildx-m2-cache.sh m2-swipes-demo desktop-linux
  ./scripts/clear-buildx-m2-cache.sh all
EOF
}

if [[ "${1:-}" == "" || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 1
fi

cache_id="$1"
builder="${2:-}"

builder_args=()
if [[ -n "$builder" ]]; then
  builder_args=(--builder "$builder")
fi

prune_all_exec_cachemount() {
  docker buildx prune --force "${builder_args[@]}" --filter "type=exec.cachemount"
}

if [[ "$cache_id" == "all" ]]; then
  prune_all_exec_cachemount
  exit 0
fi

# Try targeted cleanup first for the requested cache id.
if docker buildx prune --force "${builder_args[@]}" --filter "type=exec.cachemount" --filter "id=$cache_id"; then
  exit 0
fi

echo "Targeted prune by id failed or is not supported by your buildx version."
read -r -p "Fallback to pruning all exec.cachemount entries for this builder? [y/N] " answer

if [[ "$answer" =~ ^[Yy]$ ]]; then
  prune_all_exec_cachemount
else
  echo "Aborted."
fi


