# Moderation

In-repo classifier, versioned policies, and review workflow. Phase 4 is in this tree; Phase 5 is not claimed as finished.

Parent map: [Lunari README](../../README.md) · Domain: moderation · Feature: [complete-moderation-service](docs/features/complete-moderation-service/README.md)

## Boundary

**Owns.** HTTP intake `POST /internal/v1/moderations`. Command/result/review/policy Kafka topics. Policy versions. Outbox for results/reviews/policies. DLQ after command retries.

**Does not own.** The six-minute demo path. Live OpenAI/Gemini as if they were wired and retention-approved (Phase 5). Public client APIs — Profiles and Match call this when `MODERATION_ENABLED=true`.

**Neighbors.** [profiles](../profiles/README.md) and [match](../match/README.md). Compose `:8086`, internal.

## Contents

- Kotlin hexagonal modules under `src/main/kotlin/com/tinder/clone/moderation/`.
- Topics: `moderation.commands.v1`, `moderation.results.v1`, `moderation.reviews.v1`, `moderation.policies.v1`, `moderation.commands.dlq.v1`.
- Contracts: `docs/contracts/http/moderation-api.md`, `docs/contracts/events/moderation-events.md`.

## Decision

**Failure.** Classifier, policy versions, and review tasks are a different consistency domain from profile JSON or chat messages. Inline checks in Profiles/Match couple a model outage to writes.

**Rejected.** Inline checks in Profiles/Match. Presenting Phase 5 providers as production-ready.

**Shipped.** Own service, Phase 4 in repo. Not on the interview demo path.

**Cost.** Another Kafka topology and an enable flag. Do not demo this as finished.

## Validate

Gradle service (not Maven):

```bash
./gradlew test
```
