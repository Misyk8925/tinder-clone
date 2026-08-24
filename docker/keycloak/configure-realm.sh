#!/usr/bin/env bash
set -Eeuo pipefail

: "${KEYCLOAK_URL:?KEYCLOAK_URL is required}"
: "${KEYCLOAK_REALM:?KEYCLOAK_REALM is required}"
: "${KEYCLOAK_ADMIN_USERNAME:?KEYCLOAK_ADMIN_USERNAME is required}"
: "${KC_CLI_PASSWORD:?KC_CLI_PASSWORD is required}"

KCADM_CONFIG="$(mktemp /tmp/connect-kcadm.XXXXXX)"
cleanup() {
  rm -f "${KCADM_CONFIG}"
}
trap cleanup EXIT

authenticated=false
for attempt in $(seq 1 60); do
  if /opt/keycloak/bin/kcadm.sh config credentials \
    --config "${KCADM_CONFIG}" \
    --server "${KEYCLOAK_URL}" \
    --realm master \
    --user "${KEYCLOAK_ADMIN_USERNAME}" >/dev/null 2>&1; then
    authenticated=true
    break
  fi
  sleep 2
done

if [[ "${authenticated}" != "true" ]]; then
  echo "Keycloak did not accept deployment admin credentials within 120 seconds" >&2
  exit 1
fi

/opt/keycloak/bin/kcadm.sh update "realms/${KEYCLOAK_REALM}" \
  --config "${KCADM_CONFIG}" \
  -s displayName=Lunari \
  -s registrationAllowed=true >/dev/null

REALM_STATE="$(
  /opt/keycloak/bin/kcadm.sh get "realms/${KEYCLOAK_REALM}" \
    --config "${KCADM_CONFIG}" \
    --fields displayName,registrationAllowed
)"

if ! grep -Eq '"registrationAllowed"[[:space:]]*:[[:space:]]*true' <<<"${REALM_STATE}"; then
  echo "Keycloak realm '${KEYCLOAK_REALM}' did not enable self-registration" >&2
  exit 1
fi

if ! grep -Eq '"displayName"[[:space:]]*:[[:space:]]*"Lunari"' <<<"${REALM_STATE}"; then
  echo "Keycloak realm '${KEYCLOAK_REALM}' did not apply the Lunari display name" >&2
  exit 1
fi

echo "PASS: Keycloak realm '${KEYCLOAK_REALM}' uses the Lunari brand and allows self-registration"
