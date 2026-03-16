#!/usr/bin/env bash
# =============================================================================
# generate-certs-docker.sh
#
# Generates mTLS certificates for all services using Docker service names as
# SANs (Subject Alternative Names).  Run this script once on the machine where
# you build the project, then copy the resulting files to each server or mount
# them as Docker volumes.
#
# Usage:
#   chmod +x deploy/generate-certs-docker.sh
#   ./deploy/generate-certs-docker.sh
#
# Output:  deploy/certs/  (all .p12 keystores + shared truststore.jks)
#
# After running, copy files into each service's src/main/resources/ (for
# classpath inclusion) OR mount the certs/ directory as a volume in
# docker-compose.dokploy.yml and set the environment variables in .env:
#
#   PROFILES_KEYSTORE_PATH=/certs/profiles-service.p12
#   CONSUMER_KEYSTORE_PATH=/certs/consumer-service.p12
#   DECK_KEYSTORE_PATH=/certs/deck-service.p12
#   SUBSCRIPTIONS_GRPC_KEYSTORE_PATH=/certs/subscriptions-service.p12
#   (set corresponding TRUSTSTORE_PATH variables too)
# =============================================================================

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$CERTS_DIR"

PASSWORD="changeit"
VALIDITY_DAYS=3650

# Truststore will be built up by importing each service's public cert
TRUSTSTORE="$CERTS_DIR/truststore.jks"

# Remove old truststore so we start fresh
rm -f "$TRUSTSTORE"

generate_cert() {
  local ALIAS="$1"
  local EXTRA_SANS="$2"   # comma-separated additional DNS SANs

  local P12="$CERTS_DIR/${ALIAS}.p12"
  local CER="$CERTS_DIR/${ALIAS}.cer"

  echo "--- Generating $ALIAS ---"

  # Always include the alias itself and localhost as SANs
  local SAN="DNS:${ALIAS},DNS:localhost,IP:127.0.0.1"
  if [ -n "$EXTRA_SANS" ]; then
    SAN="${SAN},${EXTRA_SANS}"
  fi

  rm -f "$P12"

  keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -keystore "$P12" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -dname "CN=${ALIAS}, OU=internal, O=tinder-clone, L=Berlin, ST=Berlin, C=DE" \
    -ext "SAN=${SAN}"

  # Export public cert and import into shared truststore
  keytool -exportcert \
    -alias "$ALIAS" \
    -keystore "$P12" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -file "$CER" \
    -rfc

  keytool -delete -alias "$ALIAS" -keystore "$TRUSTSTORE" -storepass "$PASSWORD" 2>/dev/null || true
  keytool -importcert \
    -alias "$ALIAS" \
    -file "$CER" \
    -keystore "$TRUSTSTORE" \
    -storepass "$PASSWORD" \
    -noprompt

  echo "  Created ${ALIAS}.p12  SAN: ${SAN}"
}

# ---------------------------------------------------------------------------
# Generate one certificate per service that participates in mTLS.
# Add extra SANs if you expose the service on a custom hostname.
# ---------------------------------------------------------------------------
generate_cert "profiles-service"     ""
generate_cert "consumer-service"     ""
generate_cert "deck-service"         ""
generate_cert "subscriptions-service" ""

echo ""
echo "=== All certificates generated in $CERTS_DIR ==="
echo ""
echo "Next steps:"
echo "  1. Copy the generated files into each service's src/main/resources/"
echo "     OR mount $CERTS_DIR as /certs in docker-compose.dokploy.yml"
echo "  2. Set the *_KEYSTORE_PATH / *_TRUSTSTORE_PATH env vars in .env"
echo "  3. Rebuild and redeploy"
echo ""
echo "Verify SANs:"
for P12 in "$CERTS_DIR"/*.p12; do
  ALIAS=$(basename "$P12" .p12)
  echo "  $ALIAS:"
  keytool -list -v -keystore "$P12" -storetype PKCS12 -storepass "$PASSWORD" 2>/dev/null \
    | grep -E "DNSName|IPAddress" | sed 's/^/    /' || true
done
