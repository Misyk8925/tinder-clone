#!/usr/bin/env bash
# Bring up the local demo stack without Stripe, S3, or a hand-filled .env.
# Certs are generated into docker/certs/ if missing (password "changeit").
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
Usage: ./scripts/demo-up.sh [--check] [--] [docker compose up args...]

  --check   Validate .env.demo against Compose required vars and interpolate
            the demo overlay. Does not start containers.

Default: generate mTLS certs if missing, then:

  docker compose --env-file .env.demo \
    -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.demo.yml \
    up -d --build
EOF
}

CHECK=0
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ "${1:-}" == "--check" ]]; then
  CHECK=1
  shift
fi
if [[ "${1:-}" == "--" ]]; then
  shift
fi

compose() {
  docker compose --env-file .env.demo \
    -f docker-compose.yml \
    -f docker-compose.local.yml \
    -f docker-compose.demo.yml \
    "$@"
}

ruby scripts/validate-demo-env.rb

if [[ "$CHECK" -eq 1 ]]; then
  if command -v docker >/dev/null 2>&1; then
    compose config --quiet
    echo "PASS: demo compose interpolates from .env.demo (containers not started)"
  else
    echo "PASS: .env.demo covers required Compose vars (docker not available; skip interpolation)"
  fi
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to start the demo stack." >&2
  exit 1
fi

CERT="$ROOT/docker/certs/profiles-service.p12"
if [[ ! -f "$CERT" ]]; then
  if ! command -v keytool >/dev/null 2>&1; then
    echo "JDK keytool is required to generate mTLS certs (Java 21+)." >&2
    exit 1
  fi
  echo "Generating local mTLS certs into docker/certs/ (password changeit)"
  ./certs/generate-docker-certs.sh
fi

compose up -d --build "$@"

cat <<'EOF'

Demo stack is starting.

  Client    http://localhost:4200
  Gateway   http://localhost:8222
  Keycloak  http://localhost:9080  (admin / demo-keycloak-admin, realm spring)

Register two users, create profiles, then Discover → swipe → match → chat.
Photos stay in process memory (lost on restart). Billing is off until you
set real Stripe keys in a private .env — not this demo overlay.

Do not SQL-seed profiles. See docs/demo/seed-notes.md.
EOF
