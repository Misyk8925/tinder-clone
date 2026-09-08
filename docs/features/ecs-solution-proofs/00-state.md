# State: ecs-solution-proofs

Fallback tracker. Linear MCP is unauthorized. Current phase: **1 — concept discovery**.

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 Concept | not drafted | pending shared-understanding confirmation | — |
| 2+3 Contracts + behaviour | — | not started | — |
| 4 Implementation | — | not started | — |
| 5 Release | — | not started | — |

## Decisions

| Date | Decision | Source | Status |
|---|---|---|---|
| 2026-09-08 | Use repo-local tracker while Linear is unauthorized | same pattern as `photos-fastapi-service` | Fact |
| 2026-09-08 | Do not start ECS, Terraform, or naive baseline services in this round | workflow: no implementation before concept approval; `AGENTS.md` forbids production credentials | Fact |

No owner-approved product decisions yet.

## Open questions (frontier)

| ID | Question | Recommended answer | Owner |
|---|---|---|---|
| Q1 | What is a “proof”: synthetic load bake-off, or live user A/B on product metrics? | Synthetic sequential bake-off (RPS, error rate, p95, CPU/$, correctness of HTTP/Kafka contract). Live split needs real traffic and a stats protocol this clone does not have. | product |
| Q2 | Where does v1 run: isolated ECS Fargate sandbox, or the existing Dokploy VPS with Compose overlays? | Isolated ECS sandbox, because the request is to spend AWS credits and keep prod Compose untouched. | ops / product |
| Q3 | Hard spend cap per experiment and for the first month? | Cap each run (including teardown) and refuse to leave MSK/RDS/NAT running overnight. Number is an owner choice; suggested starting cap in the confirmation summary. | product |
| Q4 | v1 scope: only pairs that already exist, or also write disposable “simple” implementations? | v1 = existing dual implementations only (`swipes-go` vs `swipes-demo`, `location-go` vs Profiles fallback). Disposable naive decks/photos/outbox are v2 after the harness works once. | product |

## Risks

| ID | Status | Risk | Likelihood | Impact | Mitigation / decision | Owner |
|---|---|---|---|---|---|---|
| RISK-1 | Open | Full-stack ECS (Kafka + several JVM services) burns credits without a hard stop | High | High | Hard cap + teardown-on-exit; v1 measures one service, not the whole compose file | owner after Q3 |
| RISK-2 | Open | Live user A/B on Dokploy would mix experiment traffic with production Kafka groups and Redis keys | High | High | Isolated sandbox; no shared consumer groups | owner after Q1/Q2 |
| RISK-3 | Open | This Cloud environment has no AWS credentials and must not gain production keys | Certain | High | Harness is code + local contract tests; real ECS apply is a later authorized step | agent |
| RISK-4 | Open | A “simple” baseline that silently drops durability (no outbox, no `acks=all`) can “win” on RPS | Medium | High | Every result records the contract that was kept, not only latency | concept |

## Wayfinder

| ID | Question | Status | Artifact |
|---|---|---|---|
| W1 | Which solution pairs already exist and can be compared without inventing a new service? | Done | [`inventory.md`](inventory.md) |
| W2 | Measurement protocol: metrics, duration, warmup, pass/fail | Blocked on Q1 | — |
| W3 | AWS spend envelope and teardown rule | Blocked on Q3 | — |

## Decision tree

Shown before any concept or architecture draft.

```
Spend AWS credits to prove project solutions
├── [Fact] Prod today is Dokploy Compose, not ECS
│     evidence: docker-compose.yml cert mounts, README security section
├── [Fact] No ECS/Terraform/k8s deploy tree exists in the repo
├── [Fact] Dual implementations already exist for swipes and location
│     evidence: inventory.md EXP-SWIPES, EXP-LOCATION
├── [Fact] Deck-read and photos “old paths” are already extracted; naive baselines are not in repo
├── [Fact] Load evidence already exists for swipes (historical k6) and deck-read (go bench)
├── Q1 measurement kind                         [Proposed] synthetic bake-off, not live A/B
├── Q2 runtime                                  [Proposed] isolated ECS Fargate sandbox
├── Q3 spend cap                                [Open] needs a number from owner
└── Q4 first slice                              [Proposed] existing pairs only; start with swipes Java vs Go
```

## Category check (P1.5)

| Category | Status | Note |
|---|---|---|
| User & alternative | Inferred | Owner/author wants empirical proof of architecture. Today: local `wrk`/`go bench`, VPS k6 notes, compose rollback overlays. No comparable ECS record. |
| Trigger & success | Asked (Q1) | Trigger = owner starts a named experiment. Success in a test = two variants emit the same JSON metrics shape and a verdict. Success after “ship” = one committed result file plus teardown. |
| Boundaries | Asked (Q2, Q4) | Must not touch Dokploy prod, must not read production `.env`, must not keep AWS resources after the run. |
| Numbers | Asked (Q3) | Load, duration, and dollar cap are owner numbers. Historical swipes k6 on VPS is a calibration hint only, not a target. |
| Integration | Inferred | Reuse existing Dockerfiles, compose overlays, and bench entrypoints. New public client API is not required for v1. |
| Failure | Inferred | Partial AWS apply must still teardown. A failed variant is a recorded result, not a prod rollback. |
| Stakeholders | Inferred | Only the owner; no end-user change in v1 if traffic stays synthetic. |

## Shared-understanding summary (P1.7 — waiting for correction)

Problem: architectural choices in this clone (Go vs Java swipes, CQRS deck-read, FastAPI photos, location-go, outbox) are argued in code and local benches, not compared under a repeatable cloud runtime.

Proposed v1 behaviour: an isolated AWS ECS sandbox runs two implementations of one service against the same synthetic load, writes one JSON result pair, then destroys the sandbox. First experiment is `swipes-go` versus `swipes-demo` using the existing rollback overlay and benchmark auth path.

Out of scope for v1: live user traffic split, Dokploy production, writing new naive deck/photos/outbox services, spending unbounded managed Kafka/Redis.

Rejected for v1 (recommendation, not yet approved): weighted ALB live A/B — no product traffic and it would share prod state; running the entire compose stack on Fargate as the first run — too many moving parts to attribute a metric.

Please correct this summary. After that, the concept draft (FR/NFR) can be written and the phase-1 gate requested. Do not treat the recommendations above as approved.

## Phase ledger

See [`README.md`](README.md). Non-`Done` rows: P1.7–P1.12 blocked on owner confirmation; W2/W3 blocked on Q1/Q3.
