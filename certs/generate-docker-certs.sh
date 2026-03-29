#!/usr/bin/env bash
# Generate Docker-compatible mTLS certificates for all services.
#
# These certs are SEPARATE from the classpath certs used in local development.
# They are stored in docker/certs/ and mounted into containers via docker-compose volumes.
#
# The key difference: SANs include Docker service hostnames (e.g. DNS:profiles, DNS:consumer)
# in addition to localhost, so TLS hostname verification passes inside the Docker network.
#
# Usage:
#   cd <project-root>
#   bash certs/generate-docker-certs.sh
#
# Prerequisites: JDK keytool (included with any JDK installation)
# Output:        docker/certs/{service}.p12, docker/certs/truststore.jks

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
B="$(cd "$CERTS_DIR/.." && pwd)"
OUT="$B/docker/certs"
KEYSTORE_PASSWORD="${MTLS_KEYSTORE_PASSWORD:-changeit}"
TRUSTSTORE_PASSWORD="${MTLS_TRUSTSTORE_PASSWORD:-$KEYSTORE_PASSWORD}"
VALIDITY_DAYS=3650

mkdir -p "$OUT"

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   Generating Docker mTLS Certificates → docker/certs/       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  Output dir : $OUT"
echo "  Keystore password   : $KEYSTORE_PASSWORD"
echo "  Truststore password : $TRUSTSTORE_PASSWORD"
echo "  Validity   : $VALIDITY_DAYS days"
echo ""

# ─── Helper ──────────────────────────────────────────────────────────────────

generate_service_cert() {
  local alias="$1"
  local cn="$2"
  local san="$3"

  echo "▶ Generating $alias ..."

  # Remove old keystore if present
  rm -f "$OUT/$alias.p12" "$OUT/$alias.cer"

  keytool -genkeypair \
    -alias         "$alias" \
    -keyalg        RSA \
    -keysize       2048 \
    -validity      "$VALIDITY_DAYS" \
    -keystore      "$OUT/$alias.p12" \
    -storetype     PKCS12 \
    -storepass     "$KEYSTORE_PASSWORD" \
    -dname         "CN=$cn, OU=internal, O=tinder-clone, L=Berlin, ST=Berlin, C=DE" \
    -ext           "SAN=$san"

  keytool -exportcert \
    -alias     "$alias" \
    -keystore  "$OUT/$alias.p12" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASSWORD" \
    -file      "$OUT/$alias.cer" \
    -rfc

  echo "  ✓ $alias.p12 and $alias.cer created"
}

# ─── Generate per-service keypairs ───────────────────────────────────────────
#
# SANs cover:
#   DNS:<service-name>       → Spring application name / CN (used as gRPC override-authority)
#   DNS:<docker-hostname>    → Docker Compose service name (used in container URLs)
#   DNS:localhost            → local development without Docker
#   IP:127.0.0.1             → local development with IP

generate_service_cert \
  "profiles-service" \
  "profiles-service" \
  "DNS:profiles-service,DNS:profiles,DNS:localhost,IP:127.0.0.1"

generate_service_cert \
  "consumer-service" \
  "consumer-service" \
  "DNS:consumer-service,DNS:consumer,DNS:localhost,IP:127.0.0.1"

generate_service_cert \
  "deck-service" \
  "deck-service" \
  "DNS:deck-service,DNS:deck,DNS:localhost,IP:127.0.0.1"

generate_service_cert \
  "subscriptions-service" \
  "subscriptions-service" \
  "DNS:subscriptions-service,DNS:subscriptions,DNS:localhost,IP:127.0.0.1"

# ─── Build shared truststore ──────────────────────────────────────────────────

echo ""
echo "▶ Building shared truststore.jks ..."

rm -f "$OUT/truststore.jks"

for alias in profiles-service consumer-service deck-service subscriptions-service; do
  keytool -importcert \
    -alias     "$alias" \
    -file      "$OUT/$alias.cer" \
    -keystore  "$OUT/truststore.jks" \
    -storepass "$TRUSTSTORE_PASSWORD" \
    -storetype JKS \
    -noprompt
  echo "  ✓ Imported $alias into truststore"
done

# ─── Verify ──────────────────────────────────────────────────────────────────

echo ""
echo "▶ Verifying SANs ..."
for alias in profiles-service consumer-service deck-service subscriptions-service; do
  echo ""
  echo "  ── $alias ──"
  keytool -list -v \
    -keystore  "$OUT/$alias.p12" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASSWORD" 2>/dev/null \
    | grep -E "SubjectAlternativeName|DNSName|IPAddress" || echo "  (no SAN output)"
done

echo ""
echo "▶ Truststore entries:"
keytool -list \
  -keystore  "$OUT/truststore.jks" \
  -storepass "$TRUSTSTORE_PASSWORD" \
  -storetype JKS 2>/dev/null \
  | grep -v "^Keystore\|^Your\|^Certificate\|^$\|^Warning\|^--" || true

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   Done! Certs written to docker/certs/                      ║"
echo "║                                                              ║"
echo "║   Next step: docker compose up --build                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
