#!/usr/bin/env python3
from pathlib import Path
import re
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
OPENAPI = ROOT / "src/main/resources/contracts/openapi.yaml"
ASYNCAPI = ROOT / "src/main/resources/contracts/asyncapi.yaml"
HTTP_DOC = ROOT / "docs/contracts/http/moderation-api.md"
EVENT_DOC = ROOT / "docs/contracts/events/moderation-events.md"
DATA_DOC = ROOT / "docs/contracts/data/data-catalog.md"
MIGRATION = ROOT / "src/main/resources/db/migration/V1__moderation_service.sql"


def load_yaml(path: Path):
    with path.open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def resolve_pointer(document, pointer: str):
    if not pointer.startswith("#/"):
        return
    value = document
    for token in pointer[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(value, dict) or token not in value:
            raise AssertionError(f"Unresolved reference {pointer}")
        value = value[token]


def walk_refs(document, value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "$ref":
                resolve_pointer(document, child)
            else:
                walk_refs(document, child)
    elif isinstance(value, list):
        for child in value:
            walk_refs(document, child)


def validate_openapi():
    spec = load_yaml(OPENAPI)
    assert spec["openapi"].startswith("3.1."), "OpenAPI must be 3.1"
    operations = []
    operation_ids = set()
    for path, item in spec["paths"].items():
        for method in ("get", "post", "put", "patch", "delete"):
            if method not in item:
                continue
            operation = item[method]
            operation_id = operation.get("operationId")
            assert operation_id, f"Missing operationId for {method.upper()} {path}"
            assert operation_id not in operation_ids, f"Duplicate operationId {operation_id}"
            operation_ids.add(operation_id)
            assert operation.get("responses"), f"Missing responses for {operation_id}"
            operations.append((method.upper(), path))
    http_doc = HTTP_DOC.read_text(encoding="utf-8")
    for method, path in operations:
        assert f"`{method} {path}`" in http_doc, f"Readable HTTP view missing {method} {path}"
    walk_refs(spec, spec)
    return len(operations)


def validate_asyncapi():
    spec = load_yaml(ASYNCAPI)
    assert str(spec["asyncapi"]) == "3.0.0", "AsyncAPI must be 3.0.0"
    event_doc = EVENT_DOC.read_text(encoding="utf-8")
    addresses = []
    for channel in spec["channels"].values():
        address = channel["address"]
        addresses.append(address)
        assert f"`{address}`" in event_doc, f"Readable event view missing {address}"
    walk_refs(spec, spec)
    return len(addresses)


def validate_data_catalog():
    sql = MIGRATION.read_text(encoding="utf-8")
    data_doc = DATA_DOC.read_text(encoding="utf-8")
    tables = re.findall(r"CREATE TABLE\s+([a-z0-9_]+)", sql, flags=re.IGNORECASE)
    assert tables, "Migration contains no tables"
    for table in tables:
        assert f"`{table}`" in data_doc, f"Data catalog missing table {table}"
    return len(tables)


def main():
    try:
        operations = validate_openapi()
        channels = validate_asyncapi()
        tables = validate_data_catalog()
    except (AssertionError, KeyError, TypeError, yaml.YAMLError) as error:
        print(f"contract validation failed: {error}", file=sys.stderr)
        return 1
    print(f"contracts valid: {operations} HTTP operations, {channels} event channels, {tables} tables")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
