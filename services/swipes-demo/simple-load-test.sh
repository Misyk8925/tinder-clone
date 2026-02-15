#!/bin/bash

# Simple Swipes Service Load Test
# Quick test with minimal configuration

set -euo pipefail

echo "Simple Swipes Load Test"
echo "======================="
echo ""

# Configuration
HOST="http://localhost:8040"
BASE_PATH="/api/v1/swipes"
KEYCLOAK_URL="http://localhost:9080"
KEYCLOAK_REALM="spring"
KEYCLOAK_CLIENT_ID="spring-app"
KEYCLOAK_USERNAME="${KEYCLOAK_USERNAME:-kovalmisha2000@gmail.com}"
KEYCLOAK_PASSWORD="${KEYCLOAK_PASSWORD:-koval}"
WRK_THREADS="${WRK_THREADS:-10}"
WRK_CONNECTIONS="${WRK_CONNECTIONS:-1000}"
WRK_DURATION="${WRK_DURATION:-10s}"
WRK_TIMEOUT="${WRK_TIMEOUT:-5s}"

# Get JWT token
echo "Getting JWT token..."
TOKEN_RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=$KEYCLOAK_CLIENT_ID" \
  -d "username=$KEYCLOAK_USERNAME" \
  -d "password=$KEYCLOAK_PASSWORD")

if command -v jq &> /dev/null; then
    JWT_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')
else
    JWT_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" = "null" ]; then
    echo "❌ Failed to get token"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "✅ Token obtained"
echo ""

# Check JVM flags of swipes service (direct path on :8020)
if command -v lsof &> /dev/null && command -v jcmd &> /dev/null; then
  SWIPES_PID="$(lsof -t -iTCP:8020 -sTCP:LISTEN 2>/dev/null | head -n1 || true)"
  if [[ -n "${SWIPES_PID}" ]]; then
    JVM_ARGS_LINE="$(jcmd "${SWIPES_PID}" VM.command_line 2>/dev/null | awk -F'jvm_args: ' '/jvm_args:/ {print $2}')"
    if [[ "${JVM_ARGS_LINE}" == *"-XX:TieredStopAtLevel=1"* ]]; then
      echo "⚠️  swipes JVM has -XX:TieredStopAtLevel=1; this can cap peak RPS."
      echo "    Remove it from the SwipesApplication run configuration."
    fi
  fi
fi

# Test single swipe request
echo "Testing single swipe..."
PROFILE1_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
PROFILE2_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

SWIPE_DATA=$(cat <<EOF
{
  "profile1Id": "$PROFILE1_ID",
  "profile2Id": "$PROFILE2_ID",
  "decision": true
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$HOST$BASE_PATH" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$SWIPE_DATA")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Swipe created successfully"
    echo "Response: $RESPONSE_BODY"
else
    echo "❌ Failed (HTTP $HTTP_CODE)"
    echo "Response: $RESPONSE_BODY"
    exit 1
fi

echo ""
echo "Running load test (${WRK_DURATION}, ${WRK_CONNECTIONS} connections, ${WRK_THREADS} threads)..."
echo ""

FD_LIMIT="$(ulimit -n || true)"
if [[ -n "${FD_LIMIT}" && "${FD_LIMIT}" != "unlimited" ]]; then
  # Keep headroom for sockets/logging/files; very high connections can cause noisy read/timeouts.
  if (( WRK_CONNECTIONS > FD_LIMIT / 2 )); then
    echo "⚠️  WRK_CONNECTIONS=${WRK_CONNECTIONS} is high for open-file limit ${FD_LIMIT}; socket errors may dominate."
  fi
fi

# Create simple Lua script for wrk
cat > /tmp/swipe-simple.lua <<'EOF'
math.randomseed(os.time())
local status_counts = {}

local profile_ids = {}
for i = 1, 200 do
    profile_ids[i] = string.format(
        "%08x-%04x-%04x-%04x-%012x",
        math.random(0, 0xffffffff),
        math.random(0, 0xffff),
        math.random(0, 0xffff),
        math.random(0, 0xffff),
        math.random(0, 0xffffffffffff)
    )
end

request = function()
    local idx1 = math.random(1, #profile_ids)
    local idx2 = math.random(1, #profile_ids)
    while idx2 == idx1 do
        idx2 = math.random(1, #profile_ids)
    end

    local decision = math.random() > 0.3
    local body = string.format(
        '{"profile1Id":"%s","profile2Id":"%s","decision":%s}',
        profile_ids[idx1],
        profile_ids[idx2],
        tostring(decision)
    )

    return wrk.format("POST", nil, nil, body)
end

response = function(status, headers, body)
    status_counts[status] = (status_counts[status] or 0) + 1
end

done = function(summary, latency, requests)
    local keys = {}
    for code, _ in pairs(status_counts) do
        table.insert(keys, code)
    end
    table.sort(keys)

    io.write("\nHTTP status counts:\n")
    for _, code in ipairs(keys) do
        io.write(string.format("  %d: %d\n", code, status_counts[code]))
    end
end
EOF

# Run wrk
wrk --latency -t"${WRK_THREADS}" -c"${WRK_CONNECTIONS}" -d"${WRK_DURATION}" --timeout "${WRK_TIMEOUT}" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -s /tmp/swipe-simple.lua \
  "$HOST$BASE_PATH"

# Cleanup
rm -f /tmp/swipe-simple.lua

echo ""
echo "Test complete!"
