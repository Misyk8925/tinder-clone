# Architecture (interview map)

Same job as the root README and [docs/demo](../demo/README.md): one path, three boundaries, no list of fifteen hexagonal diagrams.

Use this folder for pictures. Do not open `service-refactor-plan.md` in a screening (historical).

| Artifact | Role | Runs as |
|---|---|---|
| [matching-path.puml](matching-path.puml) | Story picture: browser → gateway → deck-read / swipes / outbox | **None.** Different JARs; ArchUnit has no shared classpath |
| [profiles-layers.puml](profiles-layers.puml) | Allowed **layer** arrows inside Profiles | ArchUnit `adhereToPlantUmlDiagram` |
| [profiles-features.puml](profiles-features.puml) | Allowed **feature** arrows: photos → profile | ArchUnit; `application.moderation` cycle excluded |
| [match-modules.puml](match-modules.puml) | match ↛ conversation; conversation → moderation | ArchUnit |
| [deck-http.puml](deck-http.puml) | Deck controller → service | ArchUnit |
| [deck-read-modules.puml](deck-read-modules.puml) | Deck Read resource → service | ArchUnit |
| [deck-modules.puml](deck-modules.puml) | Deck kafka ↔ service (real cycle) | Docs only |
| [consumer-modules.puml](consumer-modules.puml) | Consumer kafka / service / outbox | Docs only |
| [moderation-layers.puml](moderation-layers.puml) | Intended Moderation layers | Docs only — bytecode arrows go both ways |
| `CleanArchitectureTest` | What UML cannot say: no Spring on domain, `*Adapter` | JUnit in Profiles |
| `DeckReadCqrsBoundaryAcceptanceTest` | Deck Read must not own Deck ranking / Redis write keys | JUnit in Deck Read |

## What to show

Open [matching-path.puml](matching-path.puml). Say: Discover is a read model; a mutual like is an outbox row; the browser JWT is not internal trust.

If they ask how a **service** is cut inside, open the inner-module table below and the `.puml` that ArchUnit loads. Do not invent a hexagon where packages are flat or cyclic.

## Services

One row each. A picture per JAR is not required; a check that you can name is.

| Service | Owns | Picture | What actually checks it |
|---|---|---|---|
| **gateway** | JWT in, static `*_SERVICE_URL` out, per-route rate limits | matching-path | `RoleBasedRateLimitFilterTest` |
| **deck-read** | `GET /api/v2/deck`, hydrate cards, call `ensure` | matching-path + **deck-read-modules.puml** | PlantUML ArchUnit + `DeckReadCqrsBoundaryAcceptanceTest` |
| **deck** | Build order, reverse index, `ensure` | matching-path + **deck-http.puml** | PlantUML ArchUnit (HTTP vs pipeline) |
| **profiles** | Profile writes, outbox, mTLS `:8011` | matching-path + **profiles-layers.puml** + **profiles-features.puml** | PlantUML ArchUnit + `CleanArchitectureTest` |
| **swipes-go** | Swipe write (Compose path) | matching-path | `go test ./...` — no ArchUnit |
| **swipes-demo** | Java rollback overlay | none | Do not present |
| **consumer** | Reciprocal like, match outbox | matching-path + **consumer-modules.puml** | `SwipeOutboxEventDispatcher` tests; UML is docs only |
| **match** | Conversations / chat | matching-path + **match-modules.puml** | PlantUML ArchUnit |
| **photos** | Bytes, variants, S3 | none | pytest; fails separately from profile JSON |
| **location-go** | Geocode / PostGIS | none | `go test ./...` |
| **subscriptions** | Stripe → premium role | none | not the six-minute path |
| **moderation** | Classifier / policy / review | **moderation-layers.puml** (intended) | in Compose; Phase 5 not claimed; not ArchUnit |
| **tinder-contracts** | Shared DTOs / events | none | compile + contract scripts |

Go and Python do not get ArchUnit. ArchUnit reads JVM bytecode. Kotlin *can*, but only when the package graph is a DAG that matches the picture.

## Inside a service

Same rule one level down. If the packages form a DAG, there is a `.puml` and a test that reads it. If they cycle or sit in one folder, name them and stop.

| Service | Inner module | Packages | Picture | Checks |
|---|---|---|---|---|
| **profiles** | layers | `domain`, `application`, `api`, `infrastructure`, `config` | [profiles-layers.puml](profiles-layers.puml) | ArchUnit |
| **profiles** | features | `application.photos` → `application.profile` | [profiles-features.puml](profiles-features.puml) | ArchUnit |
| **profiles** | moderation feature | `application.moderation` ↔ `application.profile` (usecases call the moderator; exceptions import profile/photos) | none | not ArchUnit — real cycle |
| **match** | chat vs match vs moderation client | `match` ↛ `conversation` → `moderation` | [match-modules.puml](match-modules.puml) | ArchUnit |
| **match** | security | `security` ↔ `conversation` | none | not ArchUnit — real cycle. Spring Modulith is on the classpath and unused for this reason |
| **deck** | HTTP vs pipeline | `controller` → `service` | [deck-http.puml](deck-http.puml) | ArchUnit |
| **deck** | kafka | `kafka` ↔ `service` (consumers call `DeckService`; it publishes `deck.built`) | [deck-modules.puml](deck-modules.puml) | docs only |
| **deck-read** | HTTP vs query | `resource` → `service` | [deck-read-modules.puml](deck-read-modules.puml) | ArchUnit |
| **deck-read** | messaging | `messaging` ↔ `service` | none | not ArchUnit — real cycle |
| **consumer** | kafka / service / outbox | consumers → service → outbox → kafka events | [consumer-modules.puml](consumer-modules.puml) | docs only (`kafka` ↔ `service`) |
| **moderation** | intended layers | `domain`, `application`, `infrastructure`, `config` | [moderation-layers.puml](moderation-layers.puml) | docs only. Bytecode has `domain` → `application` and `application` → `infrastructure` |
| **gateway** | — | one package `com.tinder.gateway` | none | no inner modules |
| **subscriptions** | billing vs webhook processing | `stripeServices` ↔ `events` | none | not interview; cycle |
| **photos** | — | flat `app/*.py` (`api`, `service`, `storage`, …) | none | pytest |
| **swipes-go** | — | `internal/router`, `service`, `repository`, `kafka`, `client` | none | `go test ./...` |
| **location-go** | — | one package under `cmd/location` | none | `go test ./...` |

## What not to present as finished

- A PlantUML file for every service or every folder “so it looks complete.”
- ArchUnit on a diagram that the bytecode already violates (Moderation layers, Deck kafka, Profiles `application.moderation`).
- Ranking admin / popularity as the live default.
- Moderation Phase 5.
- Eureka / Config Server (removed).

## How UML and tests stay in sync

The `.puml` that a `PlantUmlArchitectureTest` loads is the law for **those arrows**. Do not copy the same package graph into Java.

`CleanArchitectureTest` keeps only rules UML cannot express (annotations, `*Adapter`).

`matching-path.puml` is not an ArchUnit input. Sync it with the three GitHub stories, not with bytecode.

Docs-only files stay honest: they show the cycle instead of hiding it.

## Compact delivery record

| Field | Value |
|---|---|
| Outcome | Interview-shaped architecture index at service **and** inner-module grain; ArchUnit only where the package graph is a DAG |
| Out of scope | ArchUnit on Go/Python; fake hexagonal UML; deleting `CleanArchitectureTest` annotation/`*Adapter` rules; fixing Moderation/Deck/Match security cycles |
| Affected contract | none |
| Observable evidence | `PlantUmlArchitectureTest` in Profiles, Match, Deck, Deck Read; inner-module table above |
| Validation | install `tinder-contracts`, then `-Dtest=PlantUmlArchitectureTest,CleanArchitectureTest` in Profiles; `-Dtest=PlantUmlArchitectureTest` in Match, Deck, Deck Read |
| Rollback | Revert this folder and the four `PlantUmlArchitectureTest` classes / ArchUnit test deps |
