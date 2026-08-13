# Phase 4 implementation log

Started: 2026-08-11. Last updated: 2026-08-12.

Review follow-up: [MIS-28 — source import and recovery regressions](https://linear.app/mischa8925/issue/MIS-28/fix-deck-read-follow-up-regressions-in-source-import-and-recovery).

## Implemented

- Added `profile.deck-card-projection.v1` shared records and Profiles outbox publishing for profile/create/update/delete and photo mutations.
- Added Profiles aggregate version advancement for photo-only changes.
- Added restartable, keyset-paged backfill with max page 500, durable run checkpoint and `backfill_run_id` outbox linkage.
- Added internal mTLS start/resume/status API. Retrying the same `runId` resumes; another active run returns 409.
- Removed Deck Read's synchronous Profiles client and per-replica authoritative card/identity caches.
- Added named source Redis and separate cluster-mode read-model Redis clients.
- Added Kafka materializers for full cards, existing swipes and existing matches with version/set/NX idempotency.
- Added local cards, identity mapping, viewer mutations, signed cursor and atomic snapshot generations.
- Added v2 `200/202/400/401/503` handling and kept the v1 bare-array adapter on the new local projection.
- Migrated Gateway and Angular to v2 while keeping v1 routing.
- Added three-master no-replica integration Compose and six-node 3-master/3-replica production-like Compose with AOF, RDB and `noeviction`.
- Pointed the Profiles development datasource at the Compose `postgres` service by default; host or CI runs can still override `PROFILES_DB_URL`.
- Added idempotent `V2_profiles_deck_read_cqrs.sql` and wired it into clean Compose database initialization.
- Added a one-shot `profiles-migrations` dependency for existing Compose volumes and changed Profiles runtime schema handling from `update` to `validate`.
- Added a standalone Deck Read development Redis Compose on `localhost:6380`; production remains cluster-mode.
- Source entries already carrying `isSwiped=true` are filtered before fresh snapshot import, while repeat eligibility still comes only from the local swipe projection.
- A successful Deck ensure with no source snapshot now stays BUILDING through the 30-second window and then installs EMPTY without incrementing rebuild failures.
- Recovery consumer group IDs are configurable, and the runbook now requires fresh swipe/match recovery groups instead of trusting committed offsets with zero lag.
- Made the Profiles default integration suite self-contained with shared PostgreSQL/PostGIS, Kafka and Redis Testcontainers plus a local JWT decoder fixture.
- Made the Gateway application-context test self-contained with Redis Testcontainers and a console-only test logger.

## Validation evidence

- Shared contracts clean suite: **12/12 passed**, then the artifact installed successfully with Maven.
- Profiles clean suite: **292 tests, 0 failures, 0 errors, 17 skipped** with PostgreSQL/PostGIS, Kafka and Redis Testcontainers. The skips are the explicitly opt-in nine-service full-stack flow and eight live mTLS probes, not silently reported as passed.
- The Profiles SQL migration was applied as V1 → V2 → V2 by `profiles_app` against a disposable PostGIS 17 container; the new column/table/index/constraints remained valid on replay. The temporary container was removed.
- Deck Read clean suite: **49/49 passed** with Redis Testcontainers. Behavioural coverage includes source `isSwiped` filtering, successful empty-source BUILDING/EMPTY semantics, cursor mutation without skipped cards, bounded page hydration, bounded materializer retry, sanitized DLT serialization/configuration, recovery delete tombstones, independent repeat readiness and token/generation-fenced Redis writes.
- Backfill review regressions prove that active/deleted rows become UPSERT/DELETE respectively, a failed same-runId resumes at its committed cursor, and concurrent starts across replicas produce exactly one active run through a PostgreSQL advisory transaction lock.
- Gateway clean suite: **5/5 passed** with its own Redis Testcontainer and no external Logstash dependency.
- Angular full Vitest suite: **25/25 passed**; production build passed.
- OpenAPI/AsyncAPI contract validation passed.
- Main, three-master integration and standalone development Compose files parse successfully; the dev Redis runtime smoke returned `PONG` on port 6380 and was stopped with its volume preserved.
- Three-master integration cluster runtime: `cluster_state:ok`, three masters, all 16384 slots assigned (`0-5460`, `5461-10922`, `10923-16383`), AOF enabled and `noeviction` on every node. Viewer meta/fresh/lock shared slot `11099`; multi-key Lua completed without CROSSSLOT.
- Real Kafka/Redis runtime acceptance: duplicate swipe delivery committed both offsets but retained one first decision and one repeat candidate; a persistent materialization failure produced a sanitized DLT record without decision/timestamp payload fields.
- Recovery evidence: Profiles PostgreSQL same-runId/concurrent-start integration passed **2/2**. The Deck Read runtime remained NOT_READY through an interrupted/repeated BACKFILL delivery, materialized exactly two cards plus two mappings, reached zero profile-group lag, and opened fresh/repeat readiness only through separate explicit markers.
- Two-replica runtime: two separate Quarkus fast-jar JVM containers joined one Kafka group, both received non-empty assignments covering six partitions, and all six first decisions appeared once in their shared Redis. Shared-store acceptance additionally fenced a stale owner and kept generation 2 authoritative.
- Six-node production-like runtime: cluster initialized as 3 masters + 3 replicas with all 16384 slots. A fixture in slot 3443 was acknowledged by one replica; after its master stopped, the replica was promoted automatically, `cluster_state:ok` and the value remained available. The old master rejoined as a synchronized replica. The isolated containers and volumes were removed after the drill.
- Local v1/v2 shadow parity passed against one snapshot: both endpoints returned identical ordered profile IDs. Representative deployed-traffic comparison and deployment-platform backup restore ownership remain external release gates.
- Final Deck Read validation after these additions: clean suite **59/59**, selectable acceptance slice **51/51**, and two-replica Failsafe IT **1/1**.

Final command reruns and runtime Redis Cluster evidence are recorded in the release checklist.
