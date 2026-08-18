#!/usr/bin/env bash
# Generate throwaway mTLS keystores for Maven tests.
#
# Writes PKCS12 service identities plus a shared JKS truststore into the given
# directory (typically target/test-mtls). Files are not committed; CI and local
# `mvn test` recreate them in generate-test-resources.
#
# Usage:
#   bash certs/generate-test-certs.sh <output-dir>
#
# Password is always "changeit" so it matches application-test.yml.

set -euo pipefail

OUT="${1:?usage: generate-test-certs.sh <output-dir>}"
ROOT="$(cd "$(dirname "$0")" && pwd)"

mkdir -p "$OUT"
"$ROOT/generate-docker-certs.sh" "$OUT"
cp -f "$OUT/truststore.jks" "$OUT/truststore-test.jks"
rm -f "$OUT"/*.cer
