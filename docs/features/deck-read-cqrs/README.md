# Deck Read CQRS — autonomous read model

Tracked in Linear: **https://linear.app/mischa8925/project/deck-read-cqrs-autonomous-read-model-77009ca35863**

Linear is the workflow tracker and approval log. This directory contains the canonical, reviewable feature artifacts; no `00-state.md` is used.

## Current phase

**Engineering delivery complete — GO for merge.** Phase 1 and the combined Phase 2/3 gate were approved on 2026-08-11 after the scope was corrected to Deck Read only. Implementation, repository-local recovery evidence and the Phase 5 verification checklist are complete. Production readiness is not evaluated because no production deployment platform exists in this repository; deployed restore, catch-up and traffic-shadowing checks are tracked as future scope in Linear.

## Artifacts

- [Approved concept](01-concept.ru.md)
- [Contract index](02-contracts/README.md)
- [Executable behaviour and traceability](03-behaviour/README.md)
- [Implementation plan](04-implementation/plan.md)
- [Implementation log](04-implementation/log.md)
- [Recovery runbook](04-implementation/recovery-runbook.md)
- [Release checklist](05-release/checklist.md)
- [HTTP contract](../../contracts/http/deck-read.openapi.md)
- [Event contract](../../contracts/events/deck-read.asyncapi.md)
- [Profiles backfill maintenance API](../../contracts/http/profiles-deck-card-backfill.openapi.md)

## Boundary that must not drift

`services/deck` is not part of this feature. Its algorithm, synchronous Profiles/Swipes calls, Redis keys and internal ensure endpoint remain unchanged. Deck Read keeps the ensure client, reads only Deck ordering/timestamp from the existing Redis, and owns a separate Redis Cluster read model.
