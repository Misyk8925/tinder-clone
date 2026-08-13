# Profiles Deck Card backfill maintenance API

Canonical contract: [`profiles-deck-card-backfill.openapi.yaml`](profiles-deck-card-backfill.openapi.yaml).

This is an internal mTLS API on Profiles port `8011`. It is an explicit operator action, not a startup hook and not a request from Deck Read.

## Exact invocation

1. Generate one UUID and persist it in the incident/change record as `runId`.
2. Call `POST /api/v1/profiles/internal/deck-card-projection/backfills/{runId}` with the trusted internal client certificate.
3. If the HTTP call times out or Profiles restarts, repeat the same POST with the same `runId`. Never generate a replacement ID for a retry.
4. Poll `GET` on the same URI until `COMPLETED` or `FAILED`.

Profiles creates or locks the durable row for that run, reads `profiles.id > last_profile_id ORDER BY id LIMIT 500`, builds full `profile.deck-card-projection.v1` events at the current aggregate version, and commits all page outbox rows plus `last_profile_id` and `processed_count` in one PostgreSQL transaction. The ordinary outbox dispatcher publishes them. Backfill code never sends directly to Kafka.

`ENQUEUED` means all pages are in the outbox. `COMPLETED` means the run has no unpublished or dead-lettered outbox rows. It does not by itself authorize Deck Read: readiness additionally requires zero consumer lag, projection count verification and swipe/match safety checks.
