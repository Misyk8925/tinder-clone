#!/usr/bin/env python3
"""Validate Swipe Rewind OpenAPI, AsyncAPI, readable mirrors, SQL migrations, and Java contract parity."""

from pathlib import Path
import sys
import yaml

ROOT = Path(__file__).resolve().parent.parent
errors: list[str] = []


def check(cond: bool, msg: str) -> None:
    if not cond:
        errors.append(msg)


# 1. OpenAPI 3.1.0 + readable mirror
openapi_path = ROOT / "docs/contracts/http/swipes-rewind.openapi.yaml"
openapi_md_path = ROOT / "docs/contracts/http/swipes-rewind.openapi.md"

openapi = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))
openapi_md = openapi_md_path.read_text(encoding="utf-8")

check(openapi.get("openapi") == "3.1.0", "OpenAPI version must be 3.1.0")
post_rewind = openapi.get("paths", {}).get("/api/v1/swipes/rewind", {}).get("post")
check(post_rewind is not None, "Missing POST /api/v1/swipes/rewind in OpenAPI")

expected_statuses = ["200", "400", "401", "403", "404", "409", "429", "503"]
actual_statuses = [str(k) for k in (post_rewind or {}).get("responses", {}).keys()]
for st in expected_statuses:
    check(st in actual_statuses, f"OpenAPI missing status {st}")
    check(f"`{st}`" in openapi_md or f"{st} OK" in openapi_md, f"OpenAPI mirror missing status {st}")

expected_error_codes = [
    "INVALID_PAYLOAD",
    "UNAUTHENTICATED",
    "PROFILE_OWNERSHIP_DENIED",
    "NO_SWIPE_TO_REWIND",
    "REWIND_WINDOW_EXPIRED",
    "SWIPE_ALREADY_MATCHED",
    "NOT_LATEST_SWIPE",
    "RATE_LIMITED",
    "SERVICE_UNAVAILABLE",
]
schema_error_codes = (
    openapi.get("components", {})
    .get("schemas", {})
    .get("RewindErrorResponse", {})
    .get("properties", {})
    .get("code", {})
    .get("enum", [])
)
for code in expected_error_codes:
    check(code in schema_error_codes, f"OpenAPI error enum missing {code}")
    check(code in openapi_md, f"OpenAPI mirror missing error code {code}")

expected_resp_fields = [
    "revokedSwipeEventId",
    "revocationEventId",
    "profile2Id",
    "decision",
    "revokedAt",
    "alreadyRevoked",
]
actual_resp_fields = (
    openapi.get("components", {})
    .get("schemas", {})
    .get("RewindResponse", {})
    .get("required", [])
)
for field in expected_resp_fields:
    check(field in actual_resp_fields, f"RewindResponse required missing {field}")
    check(field in openapi_md, f"OpenAPI mirror missing response field {field}")

# 2. AsyncAPI 2.6.0 + readable mirror + Java shared contract parity
asyncapi_path = ROOT / "docs/contracts/events/swipe-revoked.asyncapi.yaml"
asyncapi_md_path = ROOT / "docs/contracts/events/swipe-revoked.asyncapi.md"
java_contract_path = ROOT / "services/tinder-contracts/src/main/java/com/tinder/contracts/event/v1/SwipeRevokedEventV1.java"

asyncapi = yaml.safe_load(asyncapi_path.read_text(encoding="utf-8"))
asyncapi_md = asyncapi_md_path.read_text(encoding="utf-8")
java_contract = java_contract_path.read_text(encoding="utf-8")

check(asyncapi.get("asyncapi") == "2.6.0", "AsyncAPI version must be 2.6.0")
check("swipe-revoked" in asyncapi.get("channels", {}), "AsyncAPI missing channel swipe-revoked")
check("match.created" in asyncapi.get("channels", {}), "AsyncAPI missing channel match.created")

expected_event_fields = [
    "eventId",
    "revokedSwipeEventId",
    "profile1Id",
    "profile2Id",
    "originalDecision",
    "swipedAt",
    "revokedAt",
]
actual_event_fields = (
    asyncapi.get("components", {})
    .get("schemas", {})
    .get("SwipeRevokedEventV1", {})
    .get("required", [])
)
for field in expected_event_fields:
    check(field in actual_event_fields, f"AsyncAPI SwipeRevokedEventV1 required missing {field}")
    check(field in asyncapi_md, f"AsyncAPI mirror missing field {field}")
    check(field in java_contract, f"SwipeRevokedEventV1.java missing field {field}")
check('TOPIC = "swipe-revoked"' in java_contract, "SwipeRevokedEventV1.java missing TOPIC constant")

# 3. SQL migrations + data catalog
v3_swipes = (ROOT / "migrations/migration/V3_swipes_rewind.sql").read_text(encoding="utf-8")
v2_consumer = (ROOT / "migrations/migration/V2_consumer_swipe_revocations.sql").read_text(encoding="utf-8")
data_catalog = (ROOT / "docs/contracts/data/swipe-rewind.md").read_text(encoding="utf-8")

for tbl in ["swipe_history", "swipe_revocations", "swipe_matches"]:
    check(f"CREATE TABLE IF NOT EXISTS {tbl}" in v3_swipes, f"V3_swipes_rewind.sql missing idempotent table {tbl}")
    check(tbl in data_catalog, f"Data catalog missing table {tbl}")
check(
    "CREATE TABLE IF NOT EXISTS swipe_revocations" in v2_consumer,
    "V2_consumer_swipe_revocations.sql missing idempotent table swipe_revocations",
)
check("consumer_db.swipe_revocations" in data_catalog, "Data catalog missing consumer_db.swipe_revocations")
check("dr:viewer:{viewerId}:rewound" in data_catalog, "Data catalog missing Redis key dr:viewer:{viewerId}:rewound")

if not errors:
    print("PASS: swipe-rewind contracts (OpenAPI 3.1.0, AsyncAPI 2.6.0, mirrors, SQL migrations, SwipeRevokedEventV1) validated cleanly.")
else:
    print(f"FAIL: swipe-rewind contract validation found {len(errors)} issue(s):", file=sys.stderr)
    for err in errors:
        print(f"  - {err}", file=sys.stderr)
    sys.exit(1)
