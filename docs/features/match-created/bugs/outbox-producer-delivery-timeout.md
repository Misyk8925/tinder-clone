# Bug: mutual like never creates a match or conversation

Severity: **blocker** · State: **fixed** · Scope: consumer outbox → `match.created` → match service

## Expected versus actual

Given two users like each other, Messages must show the new match (tap the bubble to open a conversation).

Actual: swipe records were mutual, but `match_db.matches` had no row and `GET my-chats` was empty. Consumer outbox rows failed with `Failed to construct kafka producer`.

## Repro

1. Local stack `docker compose -f docker-compose.yml -f docker-compose.local.yml`.
2. Two accounts like each other.
3. Open Messages for either account.

## Root cause

Consumer `spring.kafka.producer.properties.delivery.timeout.ms` was `30000`. Kafka 4 clients default `linger.ms` to `5` and `request.timeout.ms` to `30000`, so producer construction throws `ConfigException: delivery.timeout.ms should be equal to or larger than linger.ms + request.timeout.ms`. Mutual likes were stored, but `match.created` never published.

## Fix

Raise `delivery.timeout.ms` to `120000`. Outbox failure messages now include the nested cause.

## Regression

`KafkaProducerConstructionAcceptanceTest.givenConfiguredOutboxProducerWhenConstructedThenProducerStarts` — Given the consumer `application.yml` producer settings, When constructing a Kafka 4 producer, Then construction succeeds. Red on `30000`; green on `120000`.

`OutboxPublishErrorsTest.givenProducerConstructionFailureWhenSummarizedThenIncludesNestedConfigException` covers the nested last_error text.

Live recovery: unpublished outbox requeued after consumer rebuild; match `43cd6bca…` / `7e7dac54…` created.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: mutual-like already creates a match; implementation was broken |
| R.2 Project profile | Done | Repo-local bug record; English docs; native JUnit; local Compose smoke |
| R.3 Omitted phases identified | Done | Full-feature-only rows listed below |
| P1.1 Tracker/milestones | Mode-omit | Bug-fix uses this compact record |
| P1.2 Feature index | Mode-omit | Compact bug record is the tracker |
| P1.3 Decision tree | Mode-omit | Intended match.created behaviour is established |
| P1.4 Frontier interview | Mode-omit | No unresolved product decision |
| P1.5 Category check | Mode-omit | Not a new product capability |
| P1.6 Wayfinder split | Mode-omit | One diagnosed producer-config mechanism |
| P1.7 Shared-understanding gate | Mode-omit | Expected behaviour is established |
| P1.8 Concept | Mode-omit | No new concept |
| P1.9 Suggested solution alternatives | Mode-omit | Smallest safe correction |
| P1.10 Diagram | Mode-omit | No architecture change |
| P1.11 Concept self-check | Mode-omit | No concept phase |
| P1.12 Concept approval gate | Mode-omit | No owner decision required |
| P2.1 HTTP contract | Mode-omit | HTTP surface unchanged |
| P2.2 Event contract | Mode-omit | `match.created` payload unchanged |
| P2.3 WebSocket contract | Mode-omit | No WebSocket change |
| P2.4 Data contract | Mode-omit | No schema change |
| P2.5 Cross-cutting contract design | Mode-omit | Existing outbox/Kafka path restored |
| P2.6 Contract error table | Mode-omit | No boundary change |
| P2.7 Contract uncertainty | Mode-omit | None |
| P2.8 Continue to phase 3 | Mode-omit | Bug-fix does not run phase 2 |
| P3.1 Acceptance format selection | Mode-omit | Existing JUnit convention reused in B.6 |
| P3.2 FR acceptance suite | Mode-omit | No new FRs |
| P3.3 Contract-error acceptance suite | Mode-omit | No contract changes |
| P3.4 Traceability table | Mode-omit | Compact record links one repro to one regression check |
| P3.5 Combined contract check | Mode-omit | No phase-2/3 package |
| P3.6 Manual behaviour record | Mode-omit | Runtime smoke is P5.4 |
| P3.7 Combined approval gate | Mode-omit | No contract/behaviour decision required |
| B.1 Repro | Done | Mutual swipe rows vs empty matches; producer construction dump |
| B.2 Expected/actual/scope/severity | Done | Blocker; consumer outbox publish of `match.created` |
| B.3 Confirmed | Done | Live `Failed to construct kafka producer`; matches table empty |
| B.4 Root cause | Done | Kafka 4 `delivery.timeout.ms < linger.ms + request.timeout.ms` |
| B.5 Contract inspected | Done | `match.created` wire payload unchanged; conversations stay lazy on bubble tap |
| B.6 Regression red | Done | `KafkaProducerConstructionAcceptanceTest` failed with ConfigException |
| B.7 Smallest safe fix | Done | `delivery.timeout.ms: 120000` plus nested outbox error text |
| B.8 Regression green | Done | Same test + `OutboxPublishErrorsTest`; 5 tests passed |
| P4.1 Slice plan | Done | One AFK config slice; biggest risk was hiding a different producer failure |
| P4.2 Primary evidence | Done | Red/green producer construction check against `application.yml` |
| P4.3 Implementation | Done | Consumer (and match DLT) producer timeout; outbox error chain |
| P4.4 Test levels | Done | Unit + component producer construction selected. Integration/contract N/A: no broker/payload change. System smoke in P5.4. Specialist N/A |
| P4.5 Error path | Done | Nested ConfigException now stored/logged on outbox failure |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | Residual lead: `services/swipes-demo` still uses `delivery.timeout.ms: 30000` |
| P4.8 Quality gates | Done | Focused Maven tests and `git diff --check` passed |
| P4.9 Handoff | N/A | Same context continued |
| P4.10 Combined feature review | Mode-omit | One-slice bug-fix |
| P5.1 Build | Done | `./mvnw -B -ntp test -Dtest=KafkaProducerConstructionAcceptanceTest,OutboxPublishErrorsTest,KafkaConfigTest` |
| P5.2 Security scan | N/A | Timeout config and error-text change; no new auth or input surface |
| P5.3 Deploy | Done | Local `docker compose … up -d --build consumer`; unpublished outbox requeued |
| P5.4 Smoke | Done | Outbox published; match service created the live pair |
| P5.5 Rollback | Done | Restore `delivery.timeout.ms: 30000` (breaks Kafka 4 producers again) |
| P5.6 Monitoring | Done | Not yet observed beyond the local publish/match smoke |
| P5.7 Docs | Done | This bug record |
| P5.8 Open-risk view | Done | No in-scope blocker remains; swipes-demo timeout is an out-of-path lead |
