# Internal Moderation API v1

Canonical contract: [`src/main/resources/contracts/openapi.yaml`](../../../src/main/resources/contracts/openapi.yaml)

Base path: `/internal/v1`. JSON endpoints use HTTP Basic. Browser pages use a
form-created server session. Users and BCrypt hashes come from external configuration.

## Roles

| Capability | VIEWER | MODERATOR | POLICY_ADMIN |
|---|---:|---:|---:|
| Read decisions/evidence and policies | yes | yes | yes |
| Read and resolve review tasks | no | yes | yes |
| Create/validate/preview policy drafts | no | no | yes |
| Publish, activate, or roll back policy | no | no | yes |
| Open matching admin pages | yes | yes | yes |

## Endpoints

| Method and path | Purpose | Idempotency / concurrency |
|---|---|---|
| `POST /moderations` | Synchronous moderation | Required `Idempotency-Key`; same body replays, different body conflicts. |
| `GET /moderations` | Cursor-paged decision search | Safe; newest first; `limit` 1–100. |
| `GET /moderations/{decisionId}` | Decision and complete evidence | Safe. |
| `POST /policies` | Create draft | Version is globally unique. |
| `GET /policies` | Cursor-paged policy list | Safe. |
| `GET /policies/{version}` | Read draft or published version | Safe. |
| `PUT /policies/{version}` | Replace draft | Requires `If-Match`; published policy cannot change. |
| `POST /policies/{version}/validation` | Validate without mutation | Safe and repeatable. |
| `POST /policies/{version}/publication` | Publish immutable version | Requires `If-Match`; audit + outbox in one transaction. |
| `PUT /policies/{version}/activation` | Activate for global/content/locale scope | Replaces one active pointer, retaining previous version. |
| `POST /policy-activations/{activationId}/rollback` | Restore previous version | Requires `If-Match`; no policy content is mutated. |
| `POST /policy-previews` | Evaluate evidence using a draft | Does not persist a moderation decision. |
| `GET /review-tasks` | Cursor-paged queue | Safe; filter by status. |
| `GET /review-tasks/{reviewTaskId}` | Task and related decision | Safe. |
| `PUT /review-tasks/{reviewTaskId}/resolution` | Confirm, override, or escalate | Requires `If-Match`; terminal task cannot resolve twice. |
| `GET /admin` | Dashboard | Session auth; anonymous browser gets login redirect. |
| `GET /admin/decisions` | Decision/evidence search | Session auth. |
| `GET /admin/reviews` | Review queue | Session auth and role checks on mutations. |
| `GET /admin/policies` | Policy management | Session auth and `POLICY_ADMIN` on mutations. |

`ModerationRequest.conversationContext` is caller-supplied. It has at most 20 messages,
each message text is at most 4096 characters, and the service additionally enforces a
16 KiB aggregate context-text limit. The moderation and LLM adapters do not fetch more context.

## Decision semantics

| Decision | Meaning |
|---|---|
| `ALLOW` | Policy found no reason to delay or reject content. |
| `FLAG` | Content is retained for human review according to policy. |
| `BLOCK` | Policy rejects content with an auditable reason and confidence. |
| `HOLD` | A safe automated decision could not be made, for example missing policy or provider degradation. |

A provider timeout that can be persisted returns HTTP `200` with `HOLD` and creates a
review task. HTTP `503` is reserved for failures such as unavailable durable storage,
where the service cannot truthfully persist or replay the decision.

## Error contract

| Status | Code | When | Retry? | Acceptance ID |
|---:|---|---|---:|---|
| 400 | `INVALID_PAYLOAD` | Schema, cursor, limit, context, or business validation fails. | no | ERR-HTTP-400 |
| 401 | `UNAUTHENTICATED` | Credentials are absent or invalid. | no | ERR-HTTP-401 |
| 403 | `FORBIDDEN` | Authenticated user lacks the required role. | no | ERR-HTTP-403 |
| 404 | `NOT_FOUND` | Decision, policy, activation, or review task does not exist. | no | ERR-HTTP-404 |
| 409 | `IDEMPOTENCY_CONFLICT` | An idempotency key is reused for different content. | no | ERR-IDEMPOTENCY-409 |
| 409 | `VERSION_CONFLICT` | `If-Match` does not match current aggregate version. | yes, after read | ERR-VERSION-409 |
| 409 | `POLICY_VERSION_EXISTS` | A draft or published policy already has that version. | no | ERR-POLICY-EXISTS-409 |
| 413 | `PAYLOAD_TOO_LARGE` | HTTP body or configured content size is exceeded. | no | ERR-HTTP-413 |
| 422 | `PUBLISHED_POLICY_IMMUTABLE` | Caller tries to update published policy content. | no | ERR-POLICY-IMMUTABLE-422 |
| 422 | `POLICY_INVALID` | Publish or preview uses an invalid policy. | no | ERR-POLICY-INVALID-422 |
| 422 | `POLICY_NOT_PUBLISHED` | Caller tries to activate a draft. | no | ERR-POLICY-NOT-PUBLISHED-422 |
| 422 | `NO_PREVIOUS_POLICY` | Activation has no prior version to restore. | no | ERR-NO-PREVIOUS-422 |
| 422 | `REVIEW_ALREADY_RESOLVED` | A terminal review task is resolved again. | no | ERR-REVIEW-RESOLVED-422 |
| 429 | `RATE_LIMITED` | Traffic limit is exceeded. | yes, `Retry-After` | ERR-HTTP-429 |
| 503 | `SERVICE_UNAVAILABLE` | Durable processing cannot complete. | yes, `Retry-After` | ERR-HTTP-503 |

Error responses have `code`, `message`, `retryable`, optional `correlationId`, and
optional `fieldErrors`. Sensitive content and credentials never appear in an error.

## Compatibility

Fields may be added to v1 responses and event payload extensions. Required fields are
not removed or retyped within v1. Incompatible HTTP changes use `/internal/v2`.
