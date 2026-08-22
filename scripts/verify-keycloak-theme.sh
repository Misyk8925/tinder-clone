#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="${KEYCLOAK_THEME_COMPOSE_FILE:-${REPO_ROOT}/docker-compose.yml}"
THEME="${KEYCLOAK_THEME_NAME:-spring}"
THEME_TARGET="/opt/keycloak/themes"
ADMIN_USER="theme-smoke-admin"
ADMIN_PASSWORD="theme-smoke-password"
SMOKE_REALM="theme-smoke"
SMOKE_CLIENT="theme-smoke-client"
CONTAINER_NAME="connect-keycloak-theme-${RANDOM}-$(date +%s)"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/connect-keycloak-theme.XXXXXX")"
CONTAINER_STARTED=false

cleanup() {
  local exit_code=$?
  if [[ "${CONTAINER_STARTED}" == "true" ]] && docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    if [[ ${exit_code} -ne 0 ]]; then
      echo "Keycloak smoke container logs (last 100 lines):" >&2
      docker logs --tail 100 "${CONTAINER_NAME}" >&2 || true
    fi
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  fi
  rm -rf "${TEMP_DIR}"
  exit "${exit_code}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

for command_name in docker python3 curl; do
  require_command "${command_name}"
done

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not available" >&2
  exit 1
fi

COMPOSE_JSON="${TEMP_DIR}/compose.json"
docker compose -f "${COMPOSE_FILE}" config --no-interpolate --format json >"${COMPOSE_JSON}"

COMPOSE_RESULT="${TEMP_DIR}/compose-result.txt"
python3 - "${COMPOSE_JSON}" "${REPO_ROOT}/docker/keycloak/themes" "${THEME_TARGET}" >"${COMPOSE_RESULT}" <<'PY'
import json
import os
import sys

config_path, expected_source, expected_target = sys.argv[1:]
with open(config_path, encoding="utf-8") as handle:
    config = json.load(handle)

keycloak = config.get("services", {}).get("keycloak")
if not keycloak:
    raise SystemExit("docker-compose.yml has no keycloak service")

image = keycloak.get("image")
if not image:
    raise SystemExit("Keycloak service has no image")

expected_source = os.path.realpath(expected_source)
mounts = [
    mount
    for mount in keycloak.get("volumes", [])
    if mount.get("target") == expected_target
]
if len(mounts) != 1:
    raise SystemExit(f"Expected exactly one mount at {expected_target}, found {len(mounts)}")

mount = mounts[0]
if mount.get("type") != "bind":
    raise SystemExit("Keycloak theme mount must be a bind mount")
if os.path.realpath(mount.get("source", "")) != expected_source:
    raise SystemExit(f"Keycloak theme mount source must be {expected_source}")
if mount.get("read_only") is not True:
    raise SystemExit("Keycloak theme mount must be read-only")

print(image)
print(expected_source)
PY

KEYCLOAK_IMAGE="$(sed -n '1p' "${COMPOSE_RESULT}")"
THEMES_SOURCE="$(sed -n '2p' "${COMPOSE_RESULT}")"

required_files=(
  "login/theme.properties"
  "login/login.ftl"
  "login/register.ftl"
  "login/connect-components.ftl"
  "login/resources/css/login.css"
  "login/resources/js/login.js"
)

for relative_path in "${required_files[@]}"; do
  if [[ ! -r "${THEMES_SOURCE}/${THEME}/${relative_path}" ]]; then
    echo "Theme file is missing or unreadable: ${THEME}/${relative_path}" >&2
    exit 1
  fi
done

echo "PASS: Compose config mounts ${THEME} from the repository as read-only"

docker run -d --rm \
  --name "${CONTAINER_NAME}" \
  --publish "127.0.0.1::9080" \
  --env "KC_BOOTSTRAP_ADMIN_USERNAME=${ADMIN_USER}" \
  --env "KC_BOOTSTRAP_ADMIN_PASSWORD=${ADMIN_PASSWORD}" \
  --env "KC_SPI_THEME_CACHE_THEMES=false" \
  --env "KC_SPI_THEME_CACHE_TEMPLATES=false" \
  --env "KC_SPI_THEME_STATIC_MAX_AGE=-1" \
  --mount "type=bind,source=${THEMES_SOURCE},target=${THEME_TARGET},readonly" \
  "${KEYCLOAK_IMAGE}" \
  start-dev --http-port=9080 --hostname-strict=false >/dev/null
CONTAINER_STARTED=true

LIVE_MOUNT="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/opt/keycloak/themes"}}{{printf "%s|%s|%t" .Source .Destination .RW}}{{end}}{{end}}' "${CONTAINER_NAME}")"
EXPECTED_MOUNT="${THEMES_SOURCE}|${THEME_TARGET}|false"
if [[ "${LIVE_MOUNT}" != "${EXPECTED_MOUNT}" ]]; then
  echo "Live Keycloak theme mount does not match the read-only Compose contract" >&2
  echo "Expected: ${EXPECTED_MOUNT}" >&2
  echo "Actual:   ${LIVE_MOUNT}" >&2
  exit 1
fi

for relative_path in "${required_files[@]}"; do
  docker exec "${CONTAINER_NAME}" test -r "${THEME_TARGET}/${THEME}/${relative_path}"
done

PORT_MAPPING="$(docker port "${CONTAINER_NAME}" 9080/tcp | tail -n 1)"
HOST_PORT="${PORT_MAPPING##*:}"
BASE_URL="http://127.0.0.1:${HOST_PORT}"

ready=false
for _ in $(seq 1 120); do
  if curl --fail --silent --show-error \
    "${BASE_URL}/realms/master/.well-known/openid-configuration" \
    -o /dev/null 2>/dev/null; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "${ready}" != "true" ]]; then
  echo "Keycloak did not become ready within 120 seconds" >&2
  exit 1
fi

docker exec "${CONTAINER_NAME}" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://127.0.0.1:9080 \
  --realm master \
  --user "${ADMIN_USER}" \
  --password "${ADMIN_PASSWORD}" >/dev/null

docker exec "${CONTAINER_NAME}" /opt/keycloak/bin/kcadm.sh create realms \
  -s "realm=${SMOKE_REALM}" \
  -s enabled=true \
  -s registrationAllowed=true \
  -s resetPasswordAllowed=true \
  -s rememberMe=true \
  -s "loginTheme=${THEME}" \
  -s "displayName=Connect" >/dev/null

docker exec "${CONTAINER_NAME}" /opt/keycloak/bin/kcadm.sh create clients \
  -r "${SMOKE_REALM}" \
  -s "clientId=${SMOKE_CLIENT}" \
  -s enabled=true \
  -s publicClient=true \
  -s standardFlowEnabled=true \
  -s 'redirectUris=["http://127.0.0.1/callback"]' \
  -s 'webOrigins=["http://127.0.0.1"]' >/dev/null

LOGIN_HTML="${TEMP_DIR}/login.html"
COOKIE_JAR="${TEMP_DIR}/cookies.txt"
AUTH_URL="${BASE_URL}/realms/${SMOKE_REALM}/protocol/openid-connect/auth?client_id=${SMOKE_CLIENT}&redirect_uri=http%3A%2F%2F127.0.0.1%2Fcallback&response_type=code&scope=openid"
curl --fail --silent --show-error --location \
  --cookie-jar "${COOKIE_JAR}" \
  "${AUTH_URL}" \
  -o "${LOGIN_HTML}"

for marker in 'data-connect-theme="true"' 'data-auth-screen="login"' 'class="brand-mark"' 'class="register-link"'; do
  if ! grep -Fq "${marker}" "${LOGIN_HTML}"; then
    echo "Rendered login page is missing marker: ${marker}" >&2
    exit 1
  fi
done

extract_url() {
  local html_file="$1"
  local base_url="$2"
  local mode="$3"
  python3 - "${html_file}" "${base_url}" "${mode}" <<'PY'
from html.parser import HTMLParser
from urllib.parse import urljoin
import sys

html_path, base_url, mode = sys.argv[1:]

class Extractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.value = None

    def handle_starttag(self, tag, attrs):
        if self.value:
            return
        values = dict(attrs)
        classes = values.get("class", "").split()
        if mode == "register" and tag == "a" and "register-link" in classes:
            self.value = values.get("href")
        elif mode == "css" and tag == "link" and "stylesheet" in values.get("rel", "").split():
            self.value = values.get("href")
        elif mode == "js" and tag == "script" and values.get("src"):
            self.value = values.get("src")

parser = Extractor()
with open(html_path, encoding="utf-8") as handle:
    parser.feed(handle.read())
if not parser.value:
    raise SystemExit(f"Could not find {mode} URL in rendered login page")
print(urljoin(base_url, parser.value))
PY
}

REGISTER_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" register)"
CSS_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" css)"
JS_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" js)"

REGISTER_HTML="${TEMP_DIR}/register.html"
curl --fail --silent --show-error --location \
  --cookie "${COOKIE_JAR}" \
  --cookie-jar "${COOKIE_JAR}" \
  "${REGISTER_URL}" \
  -o "${REGISTER_HTML}"

for marker in 'data-connect-theme="true"' 'data-auth-screen="register"' 'id="kc-register-form"' 'class="login-link"'; do
  if ! grep -Fq "${marker}" "${REGISTER_HTML}"; then
    echo "Rendered registration page is missing marker: ${marker}" >&2
    exit 1
  fi
done

CSS_FILE="${TEMP_DIR}/login.css"
curl --fail --silent --show-error "${CSS_URL}" -o "${CSS_FILE}"
grep -Fq 'Connect Keycloak theme' "${CSS_FILE}"
grep -Fq -- '--connect-lime: #9cce2b' "${CSS_FILE}"

JS_FILE="${TEMP_DIR}/login.js"
curl --fail --silent --show-error "${JS_URL}" -o "${JS_FILE}"
grep -Fq 'data-password-toggle' "${JS_FILE}"

echo "PASS: Live container exposes the repository theme through a read-only mount"
echo "PASS: Keycloak renders Connect login and registration pages with their CSS and JavaScript"
