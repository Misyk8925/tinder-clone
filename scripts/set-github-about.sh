#!/usr/bin/env bash
# Sets GitHub About (description, website, topics). The cloud-agent token is an
# integration and returns 403 on this endpoint — run this as yourself:
#   gh auth login
#   ./scripts/set-github-about.sh
set -euo pipefail

repo="${1:-Misyk8925/tinder-clone}"

gh repo edit "$repo" \
  --description "Matching platform: CQRS deck reads, transactional outbox, mTLS service boundaries, role-aware gateway limits." \
  --homepage "https://lunari.misyk.tech" \
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

echo "Pinned issues are UI-only. Pin #35 #36 #37 on the repo Issues page."
gh repo view "$repo" --json description,homepageUrl,repositoryTopics
