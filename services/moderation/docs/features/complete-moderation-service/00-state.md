# Workflow state: complete-moderation-service

Current phase: **4 — blocked in slice 5**
Last updated: 2026-09-15

## Approvals

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 — Concept | [`concept.ru.md`](concept.ru.md) | Michael | 2026-09-06 |
| 2 — Contracts | `02-contracts/` (draft; no separate gate) | drafted | 2026-09-06 |
| 3 — Contracts + behaviour | `02-contracts/` + `03-behaviour/` | Michael | 2026-09-06 |
| 4 — Implementation | all applicable acceptance and risk-selected checks green | blocked: NFR-1 and Docker integration | — |
| 5 — Release | `05-release/checklist.md` | — | — |

## Pre-gate checks

The proposal was checked against the current Kotlin/Spring module, its synchronous ports,
policy DSL, tests, and dependency file. PostgreSQL, Redis, Kafka, Spring Security,
server-rendered UI, and real provider adapters are explicit additions rather than claims
about existing code. `./gradlew test` passed before the concept was written.

## Risk register

| ID | Raised in phase | Risk | Likelihood | Impact | Mitigation / plan | Status | Owner | Closed in phase |
|---|---|---|---|---|---|---|---|---|
| R-1 | 1 | A real provider smoke test requires credentials and network access. | High | Medium | Deterministic provider fixtures and explicit keyless fallback are green; live smoke remains a release check. | Mitigated for local/keyless scope | Owner | 4 |
| R-2 | 1 | Context may contain personal or sensitive conversation data. | Medium | High | Size limits, no-log/DLQ tests and 90-day cleanup implemented; legal approval remains a release gate. | Mitigated in code; release-gated | Owner | 4 |
| R-3 | 1 | Configured local credentials are weaker than centralized identity. | Medium | High | Restrict service to the internal network, require BCrypt hashes, secure cookies, CSRF, lockout, and credential rotation. | Accepted for this scope | Owner | — |
| R-4 | 1 | Contract details of surrounding services are outside this repository. | Medium | Medium | V1 HTTP/event contracts are validated locally; sibling integration remains explicitly outside this feature. | Accepted for feature scope | Owner (approved concept) | 4 |

## Bugs

| ID | Severity | Title | Detail file | Status | Regression test |
|---|---|---|---|---|---|

## Open questions

| # | Question | Status |
|---|---|---|
| 1 | Which real classifier/LLM model and account will be used in each environment? | Release-blocked until environment owner configures it; keyless fallback is tested. |
| 2 | What production retention period is legally approved for raw content and evidence? | Release-blocked; code default is 90 days. |
| 3 | Which Kafka topic names and ACL principals are used by the surrounding system? | Release-blocked; names remain configurable. |

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

Rerun PostgreSQL/Kafka integration and the PostgreSQL-backed 50-RPS probe in a
Docker-capable environment. Release also needs approved retention, configured provider
credentials/models and deployment topic ACLs. Production deployment was not requested.
