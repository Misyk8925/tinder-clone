# Consumer

Swipe projection and match detection. Turns durable swipe events into `match.created` without dual-writing in the swipe request.

Parent map: [Lunari README](../../README.md) · Domain: swipes / match event

## Boundary

**Owns.** Persisted swipe history used for reciprocal-like detection. Transactional outbox for swipe-saved and `match.created`. `GET /api/v1/swipes/liked-me` (premium/admin, via gateway). Internal mTLS `:8051`. Swipe-count and experiment-outcome reads used by [deck](../deck/README.md).

**Does not own.** HTTP swipe write ([swipes-go](../swipes-go/README.md)). Conversations and chat ([match](../match/README.md)). Ranking.

**Neighbors.** Consumes `swipe-created` from swipes-go and profile lifecycle events. [match](../match/README.md) consumes `match.created`. [deck](../deck/README.md) calls swipe counts / outcomes over mTLS. [gateway](../gateway/README.md) exposes liked-me only.

## Contents

- Outbox: `SwipeOutboxEventDispatcher`, `MatchOutboxBatchProcessor` — claim, publish, backoff, dead-letter (do not delete evidence).
- Liked-me query path.
- `ProfileSwipeCounts*` and `ExperimentOutcome*` for the ranker.
- Ports `:8050` (public via gateway) and `:8051` (mTLS).

## Decision

**Failure.** “Swipe saved, therefore Kafka got the match” is a lie. A process can commit Postgres and die before `send()`. A match that exists only in memory is a support incident.

**Rejected.** Dual-write in the swipe request thread. “The consumer will notice eventually” with nothing to retry.

**Shipped.** Same outbox pattern as Profiles. At-least-once; consumers (Match) must be idempotent. Failed / dead-lettered rows stay visible.

**Cost.** Lag between commit and Kafka. Swipe write and match projection are two services on purpose.

## Validate

```bash
mvn test
```

See [docs/demo/decisions.md](../../docs/demo/decisions.md) (outbox story).
