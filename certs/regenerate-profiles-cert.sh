#!/usr/bin/env bash
# Regenerate profiles-service mTLS certificate with SAN covering localhost.
# Run this script when deck-service reports: "No name matching localhost found".

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
B="$(cd "$CERTS_DIR/.." && pwd)"
PASSWORD="changeit"
VALIDITY_DAYS=3650
ALIAS="profiles-service"

echo "=== Regenerating $ALIAS mTLS certificate ==="

# Step 1: Remove old keystore so keytool creates a fresh one
if [ -f "$CERTS_DIR/$ALIAS.p12" ]; then
  echo "  Removing old $ALIAS.p12"
  rm -f "$CERTS_DIR/$ALIAS.p12"
fi

# Step 2: Generate new keypair with SAN for localhost and service DNS
keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity $VALIDITY_DAYS \
  -keystore "$CERTS_DIR/$ALIAS.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -dname "CN=profiles-service, OU=internal, O=tinder-clone, L=Berlin, ST=Berlin, C=DE" \
  -ext "SAN=DNS:profiles-service,DNS:localhost,IP:127.0.0.1"

echo "  Created $ALIAS.p12 with SAN: DNS:profiles-service, DNS:localhost, IP:127.0.0.1"

# Step 3: Export public cert
keytool -exportcert \
  -alias "$ALIAS" \
  -keystore "$CERTS_DIR/$ALIAS.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -file "$CERTS_DIR/$ALIAS.cer" \
  -rfc

echo "  Exported $ALIAS.cer"

# Step 4: Update truststore alias
keytool -delete \
  -alias "$ALIAS" \
  -keystore "$CERTS_DIR/truststore.jks" \
  -storepass "$PASSWORD" 2>/dev/null || echo "  (alias $ALIAS not in truststore yet - OK)"

keytool -importcert \
  -alias "$ALIAS" \
  -file "$CERTS_DIR/$ALIAS.cer" \
  -keystore "$CERTS_DIR/truststore.jks" \
  -storepass "$PASSWORD" \
  -noprompt

echo "  Updated $ALIAS in truststore.jks"

# Step 5: Deploy refreshed files into service resources
PROFILES_RES="$B/services/profiles/src/main/resources"
DECK_RES="$B/services/deck/src/main/resources"
CONSUMER_RES="$B/services/consumer/src/main/resources"
SUBSCRIPTIONS_RES="$B/services/subscriptions/src/main/resources"

cp "$CERTS_DIR/$ALIAS.p12" "$PROFILES_RES/$ALIAS.p12"
echo "  Deployed $ALIAS.p12 -> profiles service resources"

for RES_DIR in "$PROFILES_RES" "$DECK_RES" "$CONSUMER_RES" "$SUBSCRIPTIONS_RES"; do
  if [ -d "$RES_DIR" ]; then
    cp "$CERTS_DIR/truststore.jks" "$RES_DIR/truststore.jks"
    echo "  Updated truststore.jks in $(basename "$(dirname "$RES_DIR")")"
  fi
done

echo ""
echo "=== Done. Restart profiles-service and deck-service. ==="
echo ""
echo "Verify SAN in the new cert:"
keytool -list -v \
  -keystore "$CERTS_DIR/$ALIAS.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" 2>/dev/null \
  | grep -E "SubjectAlternativeName|DNSName|IPAddress" || true

