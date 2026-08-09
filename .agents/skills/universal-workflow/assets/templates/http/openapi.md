# HTTP API — <feature-slug>

Status: DRAFT | APPROVED <date>
Spec: `openapi.yaml` (source of truth — this file must match it)
Base path: `/api/v1/...` · Auth: Bearer JWT, scope `<scope>`

## POST /resource

Serves: FR-1, FR-2
Auth: role `<role>`, tenant-scoped
Idempotent: yes, via `Idempotency-Key` header

**Request**

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| field | string | yes | |

```json
{ "field": "example" }
```

**Response 201**

```json
{ "id": "b6b1...-uuid" }
```

**Errors**

| Status | Code | When | Retry? |
|--------|------|------|--------|
| 400 | INVALID_PAYLOAD | Schema validation failed | No |
| 401 | UNAUTHENTICATED | Missing/expired token | No |
| 403 | FORBIDDEN | | No |
| 404 | NOT_FOUND | | No |
| 409 | CONFLICT | | No |
| 429 | RATE_LIMITED | > 60 req/min per tenant | Yes, after Retry-After |
| 500 | INTERNAL | | Yes, backoff |

**Why**: <anything the schema can't express — business meaning, why this error code and not a similar one>

## Cross-cutting

- Versioning: <policy>
- Rate limits: <limits>
- Pagination: <cursor | offset>, default/max page size
- Logged / never logged: <...>

## Traceability

| FR | Endpoint |
|----|----------|
| FR-1 | POST /resource |
