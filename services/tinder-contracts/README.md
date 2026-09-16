# tinder-contracts

Local Maven library for shared HTTP DTOs, Kafka event schemas, and Deck Redis key format.

Parent map: [Lunari README](../../README.md) · Domain: shared

## Boundary

**Owns.** Types that cross a service boundary: `SharedProfileDto`, `SharedLocationDto`, `SharedPreferencesDto`, `SharedPhotoDto`, `DeckEntry`, `profile.*` events, `ProfileDeckCardProjectionEvent`, `DeckBuiltEventV1`, `DeckRedisKeys`.

**Does not own.** Runtime YAML, Compose, or behavior. Kafka/Redis *policy* ([platform](../../platform/README.md)). OpenAPI/AsyncAPI narrative docs (`docs/contracts/`).

**Neighbors.** Installed before building [deck](../deck/README.md), [deck-read](../deck-read/README.md), and [profiles](../profiles/README.md). Other Java services may depend on the same artifacts.

## Contents

```
com.tinder.contracts
├── dto/          Shared* carriers
├── event/v1/     versioned Kafka payloads
└── deck/         DeckRedisKeys
```

## Decision

**Failure.** `Shared*Dto` used to be physically copied between Profiles and Deck. The copy drifted.

**Rejected.** Each service owning a private copy. Putting policy DSL in this jar.

**Shipped.** One local Maven module. A DTO or event change is a cross-service contract change — inspect consumers first.

**Cost.** An extra `mvn install` before those three services resolve.

## Validate

```bash
mvn -B -DskipTests install
```
