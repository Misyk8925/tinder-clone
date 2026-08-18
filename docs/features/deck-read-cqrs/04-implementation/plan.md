# Phase 4 implementation plan

Status: implementation and repository-local Phase 5 verification complete.

| Slice | Scope | Status |
|---|---|---|
| 1 | Shared full-card event, Profiles LIVE dual outbox publish and aggregate versions including photos | Done |
| 2 | Explicit restartable Profiles backfill, durable checkpoint and same-transaction outbox pages | Done |
| 3 | Deck Read Kafka materializers, separate named read-model client and local identity/card/mutation projection | Done |
| 4 | Atomic generation snapshots, repeat policy, signed cursor, v2 API and v1 local adapter | Done |
| 5 | Gateway v1/v2 forwarding and Angular cursor/polling migration | Done |
| 6 | Three-master test topology, six-node production-like topology, durability settings and recovery runbook | Done; repository-local runtime and failover drills passed |

## Non-negotiable boundary

`services/deck` is unchanged. Deck Read still calls its existing ensure endpoint and reads its existing Redis ordering/timestamp. It never writes those keys and does not own scoring.

## Data placement

- Existing Redis: Deck-owned ordering and build timestamp plus all other existing Deck internals.
- New Read Cluster: non-expiring versioned cards and user→profile mappings; viewer-local current/old generations, repeat candidates, first swipes, matches, locks and metadata under `dr:*`.
- Profiles PostgreSQL: profile source of truth, normal outbox and durable backfill checkpoint.
- Kafka: at-least-once transport and bounded swipe/match replay, not the permanent backup.
