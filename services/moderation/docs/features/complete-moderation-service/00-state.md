# Workflow state: complete-moderation-service

Current phase: **4 — Implementation**
Last updated: 2026-09-06

## Approvals

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 — Concept | [`concept.ru.md`](concept.ru.md) | Michael | 2026-09-06 |
| 2 — Contracts | `02-contracts/` (draft; no separate gate) | drafted | 2026-09-06 |
| 3 — Contracts + behaviour | `02-contracts/` + `03-behaviour/` | Michael | 2026-09-06 |
| 4 — Implementation | all applicable acceptance and risk-selected checks green | — | — |
| 5 — Release | `05-release/checklist.md` | — | — |

## Pre-gate checks

The proposal was checked against the current Kotlin/Spring module, its synchronous ports,
policy DSL, tests, and dependency file. PostgreSQL, Redis, Kafka, Spring Security,
server-rendered UI, and real provider adapters are explicit additions rather than claims
about existing code. `./gradlew test` passed before the concept was written.

## Risk register

| ID | Raised in phase | Risk | Likelihood | Impact | Mitigation / plan | Status | Owner | Closed in phase |
|---|---|---|---|---|---|---|---|---|
| R-1 | 1 | A real provider smoke test requires credentials and network access. | High | Medium | Keep deterministic stub contract tests mandatory; report live smoke separately when credentials exist. | Open | Owner | — |
| R-2 | 1 | Context may contain personal or sensitive conversation data. | Medium | High | Limit context size and fields, redact logs, authorize evidence access, and define retention before release. | Open | Implementation | — |
| R-3 | 1 | Configured local credentials are weaker than centralized identity. | Medium | High | Restrict service to the internal network, require BCrypt hashes, secure cookies, CSRF, lockout, and credential rotation. | Accepted for this scope | Owner | — |
| R-4 | 1 | Contract details of surrounding services are outside this repository. | Medium | Medium | Define versioned inbound/outbound contracts and prove them locally; integrate sibling services separately. | Open | Integration owner | — |

## Bugs

| ID | Severity | Title | Detail file | Status | Regression test |
|---|---|---|---|---|---|

## Open questions

| # | Question | Status |
|---|---|---|
| 1 | Which real classifier/LLM model and account will be used in each environment? | Deferred to provider configuration; ports remain vendor-neutral. |
| 2 | What production retention period is legally approved for raw content and evidence? | Default in concept is 90 days; owner review required before release. |
| 3 | Which Kafka topic names and ACL principals are used by the surrounding system? | Resolve during integration; module contracts will use configurable names. |

## Decisions log

| Date | Decision | Why | Owner / approval source |
|---|---|---|---|
| 2026-09-06 | Preprocessing is technically neutral and never blocks on a word, URL, or regex alone. | Preserve context for classifier decisions. | Earlier owner requirement |
| 2026-09-06 | Policies are versioned, published versions immutable, and missing policy yields `HOLD`. | Auditable and fail-safe decisions. | Earlier owner requirement |
| 2026-09-06 | Add Policy API, LLM context, and an internal admin GUI. | Required management and adjudication capabilities. | Owner in current thread |
| 2026-09-06 | Do not use Keycloak; use simple local Spring Security authentication. | Explicitly requested simpler internal authentication. | Owner in current thread |
| 2026-09-06 | Approve the complete moderation-service concept. | Phase 1 gate passed after auth correction. | Owner annotation in current thread |
| 2026-09-06 | Approve HTTP/event/data contracts and executable acceptance. | Combined Phase 2/3 gate passed. | Owner annotation in current thread |
| 2026-09-06 | Use OpenAI for classification and Gemini for LLM adjudication. | Explicit provider choice. | Owner in current thread |

## Next action

Provide optional provider keys for a live smoke, or continue with slice 2 deterministic PostgreSQL implementation and keep live smoke blocked.
