# Deck semantic shadow

Repository fallback tracker for a synthetic, shadow-only semantic retrieval experiment.
The owner supplied and requested implementation of the concept on 2026-09-16.

| Phase | Artifact | State |
|---|---|---|
| 1 Concept | [01-concept.ru.md](01-concept.ru.md) | Approved by the owner's implementation request |
| 2 Contracts | [02-contracts/README.md](02-contracts/README.md) | Draft for combined gate |
| 3 Behaviour | [03-behaviour/README.md](03-behaviour/README.md) | Draft for combined gate |
| 4 Implementation | Not started | Requires combined phase 2/3 approval |
| 5 Stand run | Not started | Requires implementation and AWS preflight |

Current owner constraint (2026-09-16): **do not call AWS**. All current work is local;
no cloud preflight, resource creation, model invocation or paid run is authorized.

## Risks and questions

| ID | State | Owner / resolution |
|---|---|---|
| R1 | Open | Engineering: synthetic relevance must be independent of the embedding formula; prove with a hobby-overlap control and held-out bio traits before reporting quality. |
| R2 | Open | Engineering: DTO lacks own gender and production block/report/moderation fields; synthetic source supplies them for this experiment, so results cannot certify the production filter path. |
| R3 | Open | Engineering: two ANN top-200 pools may miss high bilateral-score pairs; compare against a broader eligible corpus sample. |
| R4 | Open | Owner: AWS run prohibited in this session; preflight, spend and cleanup remain unobserved until separately requested. |
| R5 | Open | Engineering: current simulator seeds SQL directly without profile events or hobbies; add these only in an isolated stand and exercise explicit reindex. |

Canonical artifacts live in `docs/contracts/` and the affected service test trees.
The existing user deck and Deck Read materialization remain authoritative.

## Phase ledger

| ID | Status | Evidence or reason |
|---|---|---|
| R.1 | Done | Full-feature delivery: new index and Deck shadow path |
| R.2 | Done | Repo fallback tracker; Russian concept; OpenAPI/AsyncAPI and native tests; affected-service validation |
| R.3 | N/A | Full-feature route includes all five phases |
| P1.1 | Done | This repo fallback tracker records phases and approvals; no Linear connection available |
| P1.2 | Done | This index |
| P1.3 | Done | Facts and owner choices in concept section 2 |
| P1.4 | Done | Owner chose shadow Deck, synthetic data, hobbies as shared-interest preference, and $100 limit |
| P1.5 | Done | Concept sections 1–6 cover user, trigger, boundaries, numbers, integration, failure, stakeholders |
| P1.6 | N/A | One synthetic experiment with one decision context |
| P1.7 | Done | Owner restated the plan and requested its implementation |
| P1.8 | Done | Concept FR/NFR and exclusions |
| P1.9 | Done | Concept solution and rejected alternatives |
| P1.10 | Done | Concept diagram |
| P1.11 | Done | Checked Deck ownership, profile DTO, existing simulator, events and AWS documentation |
| P1.12 | Done | Owner's 2026-09-16 implementation request approves the supplied concept |

## Phase 2 ledger

| ID | Status | Evidence or reason |
|---|---|---|
| P2.1 | N/A | No public or internal HTTP endpoint is added |
| P2.2 | Done | Existing event topics described by canonical AsyncAPI and readable view |
| P2.3 | N/A | No websocket boundary |
| P2.4 | N/A | No relational table or column; disposable OpenSearch mapping is in `docs/contracts/data/` |
| P2.5 | Done | Contract index covers synthetic-only data, versioning, log privacy, concurrency and timeout |
| P2.6 | Done | Contract index covers missing data, stale versions, provider error, timeout and cleanup |
| P2.7 | Done | AWS availability, quota and actual cost remain run-time preflight risks; AWS calls prohibited now |
| P2.8 | Done | Continued to phase 3 without a separate gate |

## Phase 3 ledger

| ID | Status | Evidence or reason |
|---|---|---|
| P3.1 | Done | Native Go and JUnit Given/When/Then tests |
| P3.2 | Blocked | FR-1 and FR-3 outside-in checks are red; FR-2 and FR-4–FR-8 checks are specified but not executable yet |
| P3.3 | Blocked | Error-path tests are specified but not executable yet |
| P3.4 | Blocked | Traceability table exists, but coverage is incomplete |
| P3.5 | Blocked | JSON/YAML parse; Go FR-1 red at behavior, Deck FR-3 red at missing port; full contract lint and remaining test compilation pending |
| P3.6 | Done | Real AWS spend/latency require a later permitted run; not represented by a fake test |
| P3.7 | Blocked | Combined package cannot be submitted for approval until P3.2–P3.5 are complete |
