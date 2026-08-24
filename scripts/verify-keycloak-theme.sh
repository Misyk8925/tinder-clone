#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="${KEYCLOAK_THEME_COMPOSE_FILE:-${REPO_ROOT}/docker-compose.yml}"
THEME="${KEYCLOAK_THEME_NAME:-spring}"
THEME_TARGET="/opt/keycloak/themes"
DOCKERFILE="${REPO_ROOT}/docker/keycloak/Dockerfile"
REALM_CONFIG_SCRIPT="${REPO_ROOT}/docker/keycloak/configure-realm.sh"
ADMIN_USER="theme-smoke-admin"
ADMIN_PASSWORD="theme-smoke-password"
SMOKE_REALM="theme-smoke"
SMOKE_CLIENT="theme-smoke-client"
CONTAINER_NAME="connect-keycloak-theme-${RANDOM}-$(date +%s)"
IMAGE_NAME="connect-keycloak-theme-image-${RANDOM}-$(date +%s)"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/connect-keycloak-theme.XXXXXX")"
CONTAINER_STARTED=false
IMAGE_BUILT=false

cleanup() {
  local exit_code=$?
  if [[ "${CONTAINER_STARTED}" == "true" ]] && docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    if [[ ${exit_code} -ne 0 ]]; then
      echo "Keycloak smoke container logs (last 100 lines):" >&2
      docker logs --tail 100 "${CONTAINER_NAME}" >&2 || true
    fi
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  fi
  if [[ "${IMAGE_BUILT}" == "true" ]]; then
    docker image rm "${IMAGE_NAME}" >/dev/null 2>&1 || true
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
python3 - "${COMPOSE_JSON}" "${REPO_ROOT}" "${DOCKERFILE}" "${THEME_TARGET}" >"${COMPOSE_RESULT}" <<'PY'
import json
import os
import re
import sys

config_path, expected_context, expected_dockerfile, expected_target = sys.argv[1:]
with open(config_path, encoding="utf-8") as handle:
    config = json.load(handle)

keycloak = config.get("services", {}).get("keycloak")
if not keycloak:
    raise SystemExit("docker-compose.yml has no keycloak service")

build = keycloak.get("build")
if not isinstance(build, dict):
    raise SystemExit("Keycloak service must build a theme image")
if os.path.realpath(build.get("context", "")) != os.path.realpath(expected_context):
    raise SystemExit("Keycloak build context must be the repository root")
dockerfile = build.get("dockerfile", "")
if not os.path.isabs(dockerfile):
    dockerfile = os.path.join(build.get("context", ""), dockerfile)
if os.path.realpath(dockerfile) != os.path.realpath(expected_dockerfile):
    raise SystemExit("Keycloak service must use docker/keycloak/Dockerfile")

mounts = [
    mount
    for mount in keycloak.get("volumes", [])
    if mount.get("target") == expected_target
]
if mounts:
    raise SystemExit(f"Keycloak theme must be baked into the image, found mount at {expected_target}")

revision = keycloak.get("labels", {}).get("com.connect.keycloak-theme-revision")
if not revision or not re.fullmatch(r"[0-9a-f]{12}", revision):
    raise SystemExit("Keycloak service must declare a 12-character theme revision label")

realm_config = config.get("services", {}).get("keycloak-realm-config")
if not realm_config:
    raise SystemExit("Compose must configure the production Keycloak realm")
if realm_config.get("image") != keycloak.get("image"):
    raise SystemExit("Keycloak and its realm configurator must use the same image")
if realm_config.get("entrypoint") != ["/opt/keycloak/bin/configure-connect-realm.sh"]:
    raise SystemExit("Keycloak realm configurator must use the baked-in script")
if realm_config.get("depends_on", {}).get("keycloak", {}).get("condition") != "service_started":
    raise SystemExit("Keycloak realm configurator must wait for Keycloak to start")

for service_name in ("profiles", "tinder-client"):
    service = config.get("services", {}).get(service_name, {})
    condition = service.get("depends_on", {}).get("keycloak-realm-config", {}).get("condition")
    if condition != "service_completed_successfully":
        raise SystemExit(f"{service_name} must wait for successful Keycloak realm configuration")

print(os.path.realpath(build.get("context", "")))
print(os.path.realpath(dockerfile))
print(revision)
PY

BUILD_CONTEXT="$(sed -n '1p' "${COMPOSE_RESULT}")"
COMPOSE_DOCKERFILE="$(sed -n '2p' "${COMPOSE_RESULT}")"
DECLARED_THEME_REVISION="$(sed -n '3p' "${COMPOSE_RESULT}")"
THEMES_SOURCE="${REPO_ROOT}/docker/keycloak/themes"

required_files=(
  "login/theme.properties"
  "login/login.ftl"
  "login/register.ftl"
  "login/template.ftl"
  "login/login-page-expired.ftl"
  "login/error.ftl"
  "login/connect-components.ftl"
  "login/resources/css/login.css"
  "login/resources/js/login.js"
  "login/resources/img/connect-icon.svg"
)

for relative_path in "${required_files[@]}"; do
  if [[ ! -r "${THEMES_SOURCE}/${THEME}/${relative_path}" ]]; then
    echo "Theme file is missing or unreadable: ${THEME}/${relative_path}" >&2
    exit 1
  fi
done

EXPECTED_THEME_REVISION="$(python3 - "${THEMES_SOURCE}/${THEME}" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

root = Path(sys.argv[1])
digest = sha256()
for path in sorted(candidate for candidate in root.rglob("*") if candidate.is_file()):
    digest.update(path.relative_to(root).as_posix().encode())
    digest.update(b"\0")
    digest.update(path.read_bytes())
    digest.update(b"\0")
print(digest.hexdigest()[:12])
PY
)"
if [[ "${DECLARED_THEME_REVISION}" != "${EXPECTED_THEME_REVISION}" ]]; then
  echo "Keycloak theme revision label is stale" >&2
  echo "Expected: ${EXPECTED_THEME_REVISION}" >&2
  echo "Actual:   ${DECLARED_THEME_REVISION}" >&2
  exit 1
fi

grep -Fq 'COPY --chown=keycloak:keycloak docker/keycloak/themes/ /opt/keycloak/themes/' "${COMPOSE_DOCKERFILE}"
grep -Fq 'docker/keycloak/configure-realm.sh /opt/keycloak/bin/configure-connect-realm.sh' "${COMPOSE_DOCKERFILE}"

if [[ ! -r "${REALM_CONFIG_SCRIPT}" ]]; then
  echo "Keycloak realm configuration script is missing or unreadable" >&2
  exit 1
fi

echo "PASS: Compose builds Keycloak with the repository theme baked into the image"
echo "PASS: Compose theme revision matches the repository and forces recreation on changes"

docker build --quiet \
  --tag "${IMAGE_NAME}" \
  --file "${COMPOSE_DOCKERFILE}" \
  "${BUILD_CONTEXT}" >/dev/null
IMAGE_BUILT=true

docker run -d --rm \
  --name "${CONTAINER_NAME}" \
  --publish "127.0.0.1::9080" \
  --env "KC_BOOTSTRAP_ADMIN_USERNAME=${ADMIN_USER}" \
  --env "KC_BOOTSTRAP_ADMIN_PASSWORD=${ADMIN_PASSWORD}" \
  --env "KC_SPI_THEME_CACHE_THEMES=false" \
  --env "KC_SPI_THEME_CACHE_TEMPLATES=false" \
  --env "KC_SPI_THEME_STATIC_MAX_AGE=-1" \
  "${IMAGE_NAME}" \
  start-dev --http-port=9080 --hostname-strict=false >/dev/null
CONTAINER_STARTED=true

LIVE_MOUNT="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/opt/keycloak/themes"}}{{.Destination}}{{end}}{{end}}' "${CONTAINER_NAME}")"
if [[ -n "${LIVE_MOUNT}" ]]; then
  echo "Live Keycloak image unexpectedly mounts ${THEME_TARGET}" >&2
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
  -s registrationAllowed=false \
  -s resetPasswordAllowed=true \
  -s rememberMe=true \
  -s "loginTheme=${THEME}" \
  -s "displayName=Lunari" >/dev/null

docker exec \
  --env "KEYCLOAK_URL=http://127.0.0.1:9080" \
  --env "KEYCLOAK_REALM=${SMOKE_REALM}" \
  --env "KEYCLOAK_ADMIN_USERNAME=${ADMIN_USER}" \
  --env "KC_CLI_PASSWORD=${ADMIN_PASSWORD}" \
  "${CONTAINER_NAME}" \
  /opt/keycloak/bin/configure-connect-realm.sh >/dev/null

REALM_STATE="$(
  docker exec "${CONTAINER_NAME}" /opt/keycloak/bin/kcadm.sh get "realms/${SMOKE_REALM}" \
    --fields displayName,registrationAllowed
)"
if ! grep -Eq '"registrationAllowed"[[:space:]]*:[[:space:]]*true' <<<"${REALM_STATE}"; then
  echo "Realm configurator did not enable self-registration" >&2
  exit 1
fi
if ! grep -Eq '"displayName"[[:space:]]*:[[:space:]]*"Lunari"' <<<"${REALM_STATE}"; then
  echo "Realm configurator did not apply the Lunari display name" >&2
  exit 1
fi
echo "PASS: Deployment realm configurator applies the Lunari brand and enables self-registration before dependent services start"

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

for marker in 'data-connect-theme="true"' 'data-auth-screen="login"' 'class="brand-mark"' 'class="register-link"' 'connect-icon.svg' '>Lunari<' 'Sign in to Lunari'; do
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
        elif mode == "reset" and tag == "a" and "reset-link" in classes:
            self.value = values.get("href")
        elif mode == "login-action" and tag == "form" and values.get("id") == "kc-form-login":
            self.value = values.get("action")
        elif mode == "css" and tag == "link" and "stylesheet" in values.get("rel", "").split():
            self.value = values.get("href")
        elif mode == "icon" and tag == "link" and "icon" in values.get("rel", "").split():
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
RESET_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" reset)"
LOGIN_ACTION_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" login-action)"
CSS_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" css)"
JS_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" js)"
ICON_URL="$(extract_url "${LOGIN_HTML}" "${BASE_URL}" icon)"

RESET_HTML="${TEMP_DIR}/reset.html"
curl --fail --silent --show-error --location \
  --cookie "${COOKIE_JAR}" \
  --cookie-jar "${COOKIE_JAR}" \
  "${RESET_URL}" \
  -o "${RESET_HTML}"

for marker in 'data-connect-theme="true"' 'data-auth-screen="flow"' 'class="brand-mark"' 'id="kc-reset-password-form"' 'connect-icon.svg'; do
  if ! grep -Fq "${marker}" "${RESET_HTML}"; then
    echo "Rendered inherited reset-password page is missing marker: ${marker}" >&2
    exit 1
  fi
done

REGISTER_HTML="${TEMP_DIR}/register.html"
curl --fail --silent --show-error --location \
  --cookie "${COOKIE_JAR}" \
  --cookie-jar "${COOKIE_JAR}" \
  "${REGISTER_URL}" \
  -o "${REGISTER_HTML}"

for marker in 'data-connect-theme="true"' 'data-auth-screen="register"' 'id="kc-register-form"' 'class="login-link"' 'Join Lunari'; do
  if ! grep -Fq "${marker}" "${REGISTER_HTML}"; then
    echo "Rendered registration page is missing marker: ${marker}" >&2
    exit 1
  fi
done

EXPIRED_ACTION_URL="$(python3 - "${LOGIN_ACTION_URL}" <<'PY'
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
import sys

parts = urlsplit(sys.argv[1])
query = dict(parse_qsl(parts.query, keep_blank_values=True))
query["session_code"] = "expired-connect-smoke"
print(urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment)))
PY
)"
EXPIRED_HTML="${TEMP_DIR}/expired.html"
curl --silent --show-error --location \
  --cookie "${COOKIE_JAR}" \
  --cookie-jar "${COOKIE_JAR}" \
  --data-urlencode "username=expired-smoke" \
  --data-urlencode "password=expired-smoke" \
  "${EXPIRED_ACTION_URL}" \
  -o "${EXPIRED_HTML}"

for marker in 'data-connect-theme="true"' 'data-auth-screen="expired"' 'id="loginRestartLink"' 'id="loginContinueLink"'; do
  if ! grep -Fq "${marker}" "${EXPIRED_HTML}"; then
    echo "Rendered expired page is missing marker: ${marker}" >&2
    exit 1
  fi
done

ERROR_HTML="${TEMP_DIR}/error.html"
ERROR_STATUS="$(
  curl --silent --show-error --location \
    --output "${ERROR_HTML}" \
    --write-out '%{http_code}' \
    "${BASE_URL}/realms/${SMOKE_REALM}/protocol/openid-connect/auth?client_id=${SMOKE_CLIENT}&redirect_uri=https%3A%2F%2Finvalid.example%2Fcallback&response_type=code&scope=openid"
)"
if [[ "${ERROR_STATUS}" != "400" ]]; then
  echo "Invalid redirect smoke expected HTTP 400, received ${ERROR_STATUS}" >&2
  exit 1
fi
for marker in 'data-connect-theme="true"' 'data-auth-screen="error"' 'class="state-message"'; do
  if ! grep -Fq "${marker}" "${ERROR_HTML}"; then
    echo "Rendered error page is missing marker: ${marker}" >&2
    exit 1
  fi
done

CSS_FILE="${TEMP_DIR}/login.css"
curl --fail --silent --show-error "${CSS_URL}" -o "${CSS_FILE}"
grep -Fq 'Lunari Keycloak theme' "${CSS_FILE}"
grep -Fq -- '--connect-lime: #9cce2b' "${CSS_FILE}"

JS_FILE="${TEMP_DIR}/login.js"
curl --fail --silent --show-error "${JS_URL}" -o "${JS_FILE}"
grep -Fq 'data-password-toggle' "${JS_FILE}"

ICON_FILE="${TEMP_DIR}/connect-icon.svg"
curl --fail --silent --show-error "${ICON_URL}" -o "${ICON_FILE}"
grep -Fq 'viewBox="0 0 48 48"' "${ICON_FILE}"
grep -Fq '#9cce2b' "${ICON_FILE}"

verify_asset_fingerprint() {
  local asset_file="$1"
  local asset_url="$2"
  local asset_kind="$3"
  python3 - "${asset_file}" "${asset_url}" "${asset_kind}" <<'PY'
from hashlib import sha256
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
import re
import sys

asset_path, asset_url, asset_kind = sys.argv[1:]
versions = parse_qs(urlsplit(asset_url).query).get("v", [])
if len(versions) != 1 or not re.fullmatch(r"[0-9a-f]{12}", versions[0]):
    raise SystemExit(f"Rendered {asset_kind} URL must contain one 12-character hex v fingerprint")

expected = sha256(Path(asset_path).read_bytes()).hexdigest()[:12]
if versions[0] != expected:
    raise SystemExit(
        f"Rendered {asset_kind} fingerprint is stale: URL has {versions[0]}, content requires {expected}"
    )
PY
}

verify_asset_fingerprint "${CSS_FILE}" "${CSS_URL}" css
verify_asset_fingerprint "${JS_FILE}" "${JS_URL}" javascript

echo "PASS: Live container exposes the complete baked-in repository theme without a host bind"
echo "PASS: Keycloak renders Lunari login and registration pages with fingerprinted CSS, JavaScript, and icon assets"
echo "PASS: Inherited Keycloak account screens render through the shared Lunari layout"
echo "PASS: Keycloak renders branded expired-session and error states"
