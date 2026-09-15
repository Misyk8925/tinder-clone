# complete-moderation-service

Tracked in: [`00-state.md`](00-state.md)

This folder tracks delivery of the moderation module as a runnable internal service.
The concept defines why and what; later phases will link canonical API, event, data,
acceptance, implementation, and release artifacts.

## What's here

| Artifact | Purpose |
|---|---|
| [`concept.ru.md`](concept.ru.md) | Phase 1 concept and requirements |
| [`00-state.md`](00-state.md) | Repo-local approvals, risks, decisions, and next action |
| [`02-contracts/`](02-contracts/README.md) | Canonical HTTP/event/data contract index |
| [`03-behaviour/`](03-behaviour/README.md) | Executable acceptance traceability |
| [`04-implementation/plan.md`](04-implementation/plan.md) | Slice plan and final ledger |
| [`04-implementation/qa-metrics.md`](04-implementation/qa-metrics.md) | Test/NFR evidence and blocked checks |
| [`05-release/checklist.md`](05-release/checklist.md) | Release handoff and remaining gates |

## Current phase

**Phase 4 — slice 5 blocked.** Functional/keyless behaviour and the warmed local HTTP
50-RPS precursor pass, but PostgreSQL/Kafka evidence is unavailable without Docker.
