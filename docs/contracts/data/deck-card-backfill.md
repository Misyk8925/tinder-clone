# Profiles Deck Card backfill state contract

This is the readable contract for FR-3/NFR-7. Phase 4 implements it in the existing Profiles PostgreSQL schema; it does not create a new database. The exact operator API is defined in [`profiles-deck-card-backfill.openapi.yaml`](../http/profiles-deck-card-backfill.openapi.yaml).

## `deck_card_projection_backfill_run`

| Column | Meaning |
|---|---|
| `run_id UUID PRIMARY KEY` | Idempotency identity supplied when initial/recovery backfill is explicitly started. |
| `status VARCHAR` | `RUNNING`, `ENQUEUED`, `COMPLETED` or `FAILED`. |
| `last_profile_id UUID NULL` | Last profileId committed in stable ascending order; the next page starts strictly after it. |
| `processed_count BIGINT` | Number of profile rows committed into outbox for this run. |
| `expected_count BIGINT` | Number of rows selected for the run, used for readiness count verification. |
| `started_at`, `updated_at`, `completed_at` | Operational timestamps; `completed_at` is nullable. |
| `last_error VARCHAR NULL` | Sanitized failure category/message; never stores a card payload or JWT. |

Only one `RUNNING` Deck Card backfill may exist. Repeating the start command with the same runId returns/resumes that run; a different runId conflicts while one is running.

## Existing `profile_event_outbox` extension

| Column | Meaning |
|---|---|
| `backfill_run_id UUID NULL` | Links a backfill-created projection row to its run. LIVE events keep null. Indexed for drain/failure checks. |

The existing unique `event_id`, retry, published and dead-letter fields remain authoritative. No card PII is duplicated into the checkpoint table.

## Transaction and paging rules

- Page size is at most 500.
- Query is keyset pagination: `profile_id > last_profile_id ORDER BY profile_id LIMIT 500`; the first page has no lower bound.
- A page transaction inserts every projection outbox row and advances `last_profile_id`/`processed_count` together. Both commit or neither commits.
- A crash before commit repeats the page; a crash after commit resumes after the stored cursor.
- Profiles created after a page cursor has passed are covered by the normal LIVE outbox path. Concurrent updates carry a newer aggregate version and cannot be overwritten by an older backfill event.
- `ENQUEUED` means all database rows have been converted to outbox rows. `COMPLETED` additionally requires zero unpublished/dead-lettered rows for the run. Deck Read readiness separately requires consumer lag zero and projection count verification.

## Invocation and restart semantics

- The operator generates one UUID and calls `POST /api/v1/profiles/internal/deck-card-projection/backfills/{runId}` over Profiles mTLS port `8011`.
- A timeout or Profiles restart is retried with the same runId. `startOrResume` locks and returns the existing checkpoint, then continues strictly after its `last_profile_id`.
- `GET` on the same URI reports the durable status. The job is not automatically started by Profiles or Deck Read.
- Only an explicitly verified recovery procedure may write `dr:read-model:ready=READY` after this run is `COMPLETED`, consumer lag is zero and projection counts match. The independent `dr:read-model:repeat-ready=READY` marker additionally requires complete seven-day swipe/match history; profile readiness alone never enables repeat fallback.
