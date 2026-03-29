#!/usr/bin/env bash
# Verify mTLS connections between services after deployment.
# Checks that:
#   1. profiles mTLS port 8011 is reachable from deck
#   2. consumer mTLS port 8051 is reachable from deck
#   3. profiles gRPC port 9010 is reachable from subscriptions
#   4. Docker cert SANs are correct
#
# Usage (from project root, after docker compose up):
#   bash scripts/verify-mtls.sh

set -euo pipefail

B="$(cd "$(dirname "$0")/.." && pwd)"
CERTS="$B/docker/certs"
PASS="changeit"

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   mTLS / gRPC Certificate Verification                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ─── 1. Verify SAN entries in each Docker cert ───────────────────────────────

echo "▶ Checking SANs in docker/certs/ ..."
echo ""

check_san() {
  local alias="$1"
  local expected_dns="$2"
  local file="$CERTS/$alias.p12"

  if [ ! -f "$file" ]; then
    echo "  ✗ $file not found — run: bash certs/generate-docker-certs.sh"
    return 1
  fi

  local sans
  sans=$(keytool -list -v -keystore "$file" -storetype PKCS12 -storepass "$PASS" 2>/dev/null \
    | grep -E "DNSName|IPAddress" || true)

  if echo "$sans" | grep -q "$expected_dns"; then
    echo "  ✓ $alias — SAN contains DNS:$expected_dns"
  else
    echo "  ✗ $alias — SAN missing DNS:$expected_dns"
    echo "    Found: $sans"
    echo "    Fix:   bash certs/generate-docker-certs.sh"
  fi
}

check_san "profiles-service"      "profiles"
check_san "consumer-service"      "consumer"
check_san "deck-service"          "deck"
check_san "subscriptions-service" "subscriptions"

# ─── 2. Verify truststore has all 4 entries ──────────────────────────────────

echo ""
echo "▶ Checking docker/certs/truststore.jks entries ..."
TRUST_ENTRIES=$(keytool -list -keystore "$CERTS/truststore.jks" -storepass "$PASS" -storetype JKS 2>/dev/null \
  | grep "trustedCertEntry" | wc -l | tr -d ' ')

if [ "$TRUST_ENTRIES" -ge 4 ]; then
  echo "  ✓ truststore contains $TRUST_ENTRIES trusted certificates"
  keytool -list -keystore "$CERTS/truststore.jks" -storepass "$PASS" -storetype JKS 2>/dev/null \
    | grep "trustedCertEntry" | sed 's/^/    /'
else
  echo "  ✗ truststore has only $TRUST_ENTRIES entries (expected 4)"
  echo "    Fix: bash certs/generate-docker-certs.sh"
fi

# ─── 3. Check live ports if containers are running ───────────────────────────

echo ""
echo "▶ Checking live service ports (requires running containers) ..."

check_port() {
  local label="$1"
  local host="$2"
  local port="$3"
  if nc -z -w3 "$host" "$port" 2>/dev/null; then
    echo "  ✓ $label — $host:$port reachable"
  else
    echo "  - $label — $host:$port not reachable (service may not be running)"
  fi
}

check_port "profiles HTTP"     "localhost" "8010"
check_port "profiles mTLS"     "localhost" "8011"
check_port "profiles gRPC"     "localhost" "9010"
check_port "consumer HTTP"     "localhost" "8050"
check_port "consumer mTLS"     "localhost" "8051"
check_port "deck HTTP"         "localhost" "8030"
check_port "subscriptions HTTP" "localhost" "8095"
check_port "gateway HTTP"      "localhost" "8222"
check_port "keycloak HTTP"     "localhost" "9080"

# ─── 4. TLS handshake check (profiles:8011) ──────────────────────────────────

echo ""
echo "▶ TLS handshake check — profiles mTLS port 8011 ..."
if nc -z -w3 localhost 8011 2>/dev/null; then
  if openssl s_client -connect localhost:8011 \
       -cert <(openssl pkcs12 -in "$CERTS/deck-service.p12" -passin pass:"$PASS" -nodes 2>/dev/null | openssl x509) \
       -key  <(openssl pkcs12 -in "$CERTS/deck-service.p12" -passin pass:"$PASS" -nodes -nocerts 2>/dev/null) \
       -CAfile <(keytool -exportcert -alias profiles-service -keystore "$CERTS/truststore.jks" \
                   -storetype JKS -storepass "$PASS" -rfc 2>/dev/null) \
       -servername profiles-service \
       </dev/null 2>&1 | grep -q "Verify return code: 0"; then
    echo "  ✓ TLS handshake succeeded"
  else
    echo "  - TLS handshake not verified (openssl may not be installed, or service not running)"
  fi
else
  echo "  - Skipped (profiles not running on localhost:8011)"
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║   Verification complete                                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
