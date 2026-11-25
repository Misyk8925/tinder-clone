#!/usr/bin/env bash
# record-by-commit.sh: run recorded Playwright e2e tests keyed by HEAD commit hash (no PR required).
#
#   ./e2e/record-by-commit.sh [--base-url URL] [-- <playwright args>]
#
# Output: e2e/out/<sha8>/<timestamp>-<sha8>/{report/,results.json,metadata.json}
set -euo pipefail

PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.65.1-noble"
ACCOUNTS_ENV="${E2E_ACCOUNTS_ENV:-$HOME/.config/e2e/accounts.env}"
export DOCKER_HOST="${DOCKER_HOST:-unix:///run/user/$(id -u)/docker.sock}"

die() { echo "record-by-commit: $*" >&2; exit 2; }

base_url="${E2E_BASE_URL:-https://lunari.misyk.tech}"
while [ $# -gt 0 ]; do
  case "$1" in
    --base-url) base_url="${2:-}"; shift 2 ;;
    --base-url=*) base_url="${1#--base-url=}"; shift ;;
    --) shift; break ;;
    -h|--help) sed -n '2,6p' "$0"; exit 0 ;;
    *) die "unknown argument: $1 (playwright args go after --)" ;;
  esac
done

[[ "$base_url" =~ ^https?://[A-Za-z0-9.:-]+/?$ ]] || die "--base-url must be a bare origin like https://host"

repo="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git checkout"
[ -f "$repo/e2e/package.json" ] || die "$repo/e2e/package.json not found"
sha="$(git -C "$repo" rev-parse HEAD)"
sha8="${sha:0:8}"
branch="$(git -C "$repo" rev-parse --abbrev-ref HEAD)"
subject="$(git -C "$repo" log -1 --format=%s)"
dirty="false"; [ -z "$(git -C "$repo" status --porcelain -- e2e)" ] || dirty="true"

run_id="$(date -u +%Y%m%dT%H%M%SZ)-${sha8}"
out_dir="$repo/e2e/out/$sha8/$run_id"
mkdir -p "$out_dir"

env_args=()
if [ -f "$ACCOUNTS_ENV" ]; then
  env_args=(--env-file "$ACCOUNTS_ENV")
else
  echo "record-by-commit: $ACCOUNTS_ENV missing; account tests may fail" >&2
fi

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "record-by-commit: $branch@$sha8 -> $base_url (run $run_id)"

set +e
docker run --rm --init \
  --name "e2e-commit-$run_id" \
  --memory 2g --cpus 2 --pids-limit 1024 --shm-size 1g \
  --cap-drop ALL --security-opt no-new-privileges \
  -v "$repo/e2e:/src:ro" \
  -v "$out_dir:/out" \
  -v e2e-npm-cache:/root/.npm \
  "${env_args[@]}" \
  -e E2E_BASE_URL="$base_url" \
  -e E2E_ACCOUNTS="keep" \
  -e E2E_OUTPUT_DIR=/out \
  -e E2E_SLOWMO_MS="${E2E_SLOWMO_MS:-0}" \
  -e E2E_PACE="${E2E_PACE:-user}" \
  -e CI=1 \
  "$PLAYWRIGHT_IMAGE" \
  bash -c 'set -e
    mkdir -p /tmp/e2e && cd /src && tar --exclude=./node_modules --exclude=./out --exclude=./test-results -cf - . | tar -C /tmp/e2e -xf -
    cd /tmp/e2e && npm ci --no-audit --no-fund --loglevel=error
    status=0; npx playwright test "$@" || status=$?
    find /out/test-results -type f \( -name "*.webm" -o -name "*.zip" -o -name "*.png" \) -delete 2>/dev/null || true
    exit $status' record-by-commit "$@"
status=$?
set -e
finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

result="failed"; [ "$status" -eq 0 ] && result="passed"
[ -f "$out_dir/results.json" ] || result="error"

M_RUN_ID="$run_id" M_RESULT="$result" M_EXIT="$status" M_SHA="$sha" \
M_BRANCH="$branch" M_SUBJECT="$subject" M_DIRTY="$dirty" M_BASE_URL="$base_url" \
M_STARTED="$started_at" M_FINISHED="$finished_at" M_AGENT="${E2E_AGENT:-$USER}" \
python3 -I - "$out_dir/metadata.json" <<'PY'
import json, os, sys
e = os.environ
meta = {
    "schema": 1,
    "commit": e["M_SHA"][:8],
    "sha": e["M_SHA"],
    "run_id": e["M_RUN_ID"],
    "result": e["M_RESULT"],
    "exit_code": int(e["M_EXIT"]),
    "branch": e["M_BRANCH"],
    "subject": e["M_SUBJECT"],
    "e2e_dirty": e["M_DIRTY"] == "true",
    "base_url": e["M_BASE_URL"],
    "started_at": e["M_STARTED"],
    "finished_at": e["M_FINISHED"],
    "agent": e["M_AGENT"],
}
with open(sys.argv[1], "w") as f:
    json.dump(meta, f, indent=2)
PY

echo "record-by-commit: $result (exit $status)"
echo "  Report: $out_dir/report/index.html"
find "$out_dir/report/data" -name "*.webm" -print 2>/dev/null | sed 's/^/  Video:  /' || true
exit "$status"
