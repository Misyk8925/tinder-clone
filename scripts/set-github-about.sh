#!/usr/bin/env bash
# Sets GitHub About (description, website, topics). The cloud-agent token is an
# integration and returns 403 on this endpoint — run this as yourself:
#   gh auth login
#   ./scripts/set-github-about.sh
set -euo pipefail

repo="${1:-Misyk8925/tinder-clone}"
homepage="https://lunari.misyk.tech"

# Do not advertise a dead stand: only set the website when it answers 2xx/3xx.
homepage_args=()
status="$(curl -sS -o /dev/null -m 20 -w '%{http_code}' "$homepage" || echo 000)"
if [[ "$status" =~ ^[23] ]]; then
  homepage_args=(--homepage "$homepage")
else
  echo "Skipping website: $homepage answered $status. Re-run after the live-stand preflight is green." >&2
fi

gh repo edit "$repo" \
  --description "Matching platform: CQRS deck reads, transactional outbox, mTLS service boundaries, role-aware gateway limits." \
  ${homepage_args[@]+"${homepage_args[@]}"} \
  --add-topic java \
  --add-topic spring-boot \
  --add-topic quarkus \
  --add-topic apache-kafka \
  --add-topic cqrs \
  --add-topic keycloak \
  --add-topic matching \
  --add-topic microservices \
  --add-topic redis \
  --add-topic postgresql

echo "Pinned issues are UI-only. Pin issues labeled story on the repo Issues page."
gh repo view "$repo" --json description,homepageUrl,repositoryTopics
