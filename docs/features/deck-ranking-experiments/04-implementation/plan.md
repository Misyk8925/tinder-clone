# Implementation plan

| # | Slice | Observable result | State |
|---|---|---|---|
| 1 | Named scoring variants + deterministic assignment | `baseline` reproduces today's score exactly; `reciprocal` ranks a mutually-reachable candidate above an unreachable one; a viewer's variant is reproducible from the id alone | **Done** |
| 2 | Experiment registry in a new `deck` Postgres schema + admin API | An experiment can be created, started and stopped over HTTP and survives a Redis flush | **Done** |
| 3 | Separate admin application | The owner manages experiments in a UI behind the Keycloak `admin` role | **Done** |
| 4a | Population generator and latent decision model | ≥100k reproducible agents whose one-sided acceptance rises from exactly zero with a swept asymmetry parameter | **Done** |
| 4b | Swipe driver | Agents read their deck and swipe through it, so ranking order reaches the outcome | **Done** |
| 5 | Outcome screen | Viewers, swipes, likes, matches and matches per 100 **swipes**, per variant, with 95% intervals | **Done** |
| 6a | Offline screen: is a paid run worth making? |  A sweep of `random` / `baseline` / `reciprocal` / `oracle` with no stack and no cost | **Done** |
| 6b | Run `baseline` vs `reciprocal` on the AWS stand |A match-rate curve against preference asymmetry — **skipped entirely if NFR-10 fires**, that is if `baseline` already reaches 95% of `oracle` | |

## Slice 1 — record

- **Observable result:** FR-1 to FR-4 and FR-9's mechanism.
- **Dependencies:** none. **HITL/AFK:** HITL — the variant weights mirror the existing ones by design, but the split and salt are owner settings from slice 2 onwards.
- **Files:** `config/ScoringProperties`, `config/ScoringConfig`, `service/ScoringService`, `service/scoring/VariantAssigner`, `service/scoring/ReciprocalAgeStrategy`, `service/scoring/ReciprocalProximityStrategy`, `service/scoring/AgeCompatibilityStrategy`, `service/scoring/LocationProximityStrategy`, `src/main/resources/application.yml`.
- **Migrations:** none — `deck` still has no database until slice 2.
- **Biggest risk:** `baseline` silently drifting from the pre-variant behaviour. Pinned by `ScoringServiceVariantTest.baselineSumsExactlyTheTwoOriginalStrategies`, which hand-sums the two original strategies rather than trusting the catalogue.

## Slice 2 — record

- **Observable result:** FR-5, FR-6 (over HTTP), FR-9.
- **Dependencies:** slice 1. **HITL/AFK:** HITL — `DECK_DB_PASSWORD` has to be set before the stack starts.
- **Files:** `experiment/*` (7 new), `service/ScoringService`, `config/ScoringProperties`, `pom.xml`, `src/main/resources/db/migration/V1__deck_experiment.sql`, `application.yml`, `docker/postgres/init-databases.sh`, `docker-compose.yml`, `.env.prod.example`.
- **Migrations:** `V1__deck_experiment.sql`, run by the service's own Flyway — the same shape `moderation` already uses. Forward-only; the tables are new, so rollback is dropping them.
- **Biggest risk:** deck gains its first startup dependency on a database. Held by `DeckApplicationTests`, which loads the full context against an unreachable datasource and still starts.

## Slice 3 — record

- **Observable result:** FR-6 end to end, NFR-3.
- **Dependencies:** slices 1-2. **HITL/AFK:** HITL — a Keycloak user needs the `ADMIN` realm role before the app is usable.
- **Files:** `clients/tinder-admin/**` (new app), `services/gateway/src/main/resources/application.yml`, `services/gateway/src/test/java/com/tinder/gateway/GatewayRouteConfigurationTest.java`, `docker/keycloak/configure-realm.sh`, `docker-compose.yml`.
- **Migrations:** none.
- **Biggest risk:** an operator tool reachable without the `ADMIN` role. Held by the gateway rule that already guards `/api/v1/admin/**`, pinned by two new route tests, and by publishing no port for the container.

## Slice 4a — record

- **Observable result:** FR-8 (population half), FR-10, FR-11, FR-13, FR-14, NFR-4.
- **Dependencies:** none — pure, runs offline. **HITL/AFK:** AFK; no owner decision left open.
- **Files:** `services/deck/load-tests/deck-sim/**` (new Go tool, no external dependencies).
- **Migrations:** none.
- **Biggest risk:** a population whose latent model the ranker can express, which would decide the experiment by construction. Held by `TestLatentTasteSeparatesCandidatesTheRankerCannotDistinguish` and `TestAppealSeparatesOtherwiseIdenticalCandidates`, which pin that two candidates identical in every feature the ranker sees are still separated by the simulated viewer.

## Slice 4b — record

- **Observable result:** FR-8 (driver half); the token question is answered.
- **Dependencies:** slice 4a. **HITL/AFK:** HITL — a bench deployment must be pointed at the simulator's issuer before a live run.
- **Files:** `services/deck/load-tests/deck-sim/{token,driver}.go`, their tests, `main.go` subcommands, README.
- **Migrations:** none.
- **Biggest risk:** a self-signed issuer trusted somewhere it should not be. Held by writing the key `0600`, by the issuer being a bench-only configuration value, and by the README stating it plainly.

## Slice 5 — record

- **Observable result:** FR-7 and NFR-5.
- **Dependencies:** slices 1-4. **HITL/AFK:** HITL — the denominator deviates from the literal FR-7 wording and needs an owner decision (see below).
- **Files:** `services/consumer/.../{ExperimentOutcomeController,ExperimentOutcomeRepository}.java`, `ConsumerSecurityConfig`, `services/deck/.../experiment/{ViewerOutcome,VariantOutcome,OutcomeAggregator}.java`, `adapters/ConsumerOutcomesHttp.java`, `ExperimentAdminController`, `clients/tinder-admin/src/{api.ts,main.ts,styles.css}`.
- **Migrations:** none. `swipe_events` has no index on `created_at`, so the window query is a sequential scan — acceptable for a bench that reads it once, and adding one would mean editing a bootstrap SQL file that an existing volume never re-runs.
- **Biggest risk:** attributing outcomes to the wrong arm. Nothing recorded the assignment, so the aggregator has to reproduce it; pinned by a test that re-derives every viewer's arm with an independent `VariantAssigner` call.

## Slice 6a — record

- **Observable result:** FR-12, and the NFR-10 decision.
- **Dependencies:** slice 4a. **HITL/AFK:** HITL — the result asks the owner a question the concept did not anticipate.
- **Files:** `services/deck/load-tests/deck-sim/{ranking,screen}.go` and their tests, `main.go` screen subcommand, README.
- **Migrations:** none.
- **Biggest risk:** the ranking formulas are duplicated from the Java strategies, so the screen can drift into measuring a ranking the service no longer uses. Pinned on the Go side only; nothing pins the Java side.
