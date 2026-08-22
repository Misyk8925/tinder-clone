# Deck Read always returns 503 because the Read Cluster advertises port 0

## Status

Fixed locally on 2026-08-22. Authenticated Discover smoke remains with the user.

Severity: blocker · Scope: Deck Read API/worker health and `GET /api/v1/deck`, `GET /api/v2/deck`

## Expected versus actual

Given a running local stack, Deck Read must be healthy and serve the deck APIs from the Read Cluster.

Actual: `deck-read-api` and `deck-read-worker` stayed unhealthy. `/q/health` was `503`. Authenticated deck requests failed the same way.

## Repro

1. `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d`
2. Recreate Redis containers while keeping `deck-read-redis-*-data` volumes.
3. `wget -qO- http://localhost:8040/q/health` inside `deck-read-api`.

Observed 2026-08-22: health check `Redis connection health check DOWN` with `Connection refused: deck-read-redis-1/172.23.0.6:0`. `CLUSTER NODES` on the seed node showed `slave,noaddr` and `:0@0` replicas.

## Root cause

Redis Cluster persisted `nodes.conf` with previous container IPs. After recreate, the seed node still advertised disconnected replicas as `:0@0`. Vert.x cluster mode connects to every `CLUSTER SLOTS` endpoint, including port 0, so the named `read-model` client never became usable. Quarkus reported Redis health `DOWN` and deck queries recovered as `503`.

A second local gate: Compose `prod,api` hardcoded `%prod.deck-read.read-model.require-ready-marker=true`, so `DECK_READ_REQUIRE_READY_MARKER=false` was ignored. With no `dr:read-model:ready=READY` key, every authenticated deck call stayed `503 READ_MODEL_NOT_READY` even after Redis health was `UP`.

## Fix and regression evidence

Redis nodes announce stable Compose hostnames (`cluster-announce-hostname` + `cluster-preferred-endpoint-type hostname`). Cluster init refuses `cluster_state:ok` when any node still reports `:0@0` or `noaddr`. Local override sets `DECK_READ_REQUIRE_READY_MARKER=false`.

`DeckReadCqrsBoundaryAcceptanceTest` — Given Docker IP churn, When the Read Cluster is configured, Then every node advertises a stable hostname and data port; init rejects `:0`/`noaddr`; local Compose does not require the production ready marker. Red on the previous Compose/init files (3 failures). Green after the fix: 11/11.

Broken existing volumes need a one-time wipe; init no longer treats that topology as success.

Live after wipe + recreate: API and worker `/q/health` `UP` with `read-model: PONG`; unauthenticated `GET /api/v2/deck` is `401`, not `503`.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: established deck-available behaviour was violated |
| R.2 Project profile | Done | Repo-local bug record; English; native JUnit acceptance; local Compose release |
| R.3 Omitted phases identified | Done | Full-feature-only rows listed below |
| P1.1–P1.12 Concept ceremony | Mode-omit | Bug-fix does not run phase 1 |
| P2.1–P2.8 Contract ceremony | Mode-omit | HTTP/error contract unchanged; 503 remains valid for true recovery |
| P3.1–P3.7 Behaviour ceremony | Mode-omit | Existing JUnit convention reused in B.6 |
| B.1 Repro | Done | Live `/q/health` 503 + `deck-read-redis-1:0` |
| B.2 Expected/actual/scope/severity | Done | Recorded above; blocker; Deck Read health and v1/v2 |
| B.3 Confirmed | Done | Reproduced on the running local stack |
| B.4 Root cause | Done | Stale `nodes.conf` advertises `:0`; Vert.x dials every slot endpoint |
| B.5 Contract inspected | Done | `READ_MODEL_NOT_READY` stays the recovery contract; local marker is operational |
| B.6 Regression red | Done | 3 new architecture checks failed before the compose/init strings existed |
| B.7 Smallest safe fix | Done | Hostname announce + init reject + local marker |
| B.8 Regression green | Done | `mvn -B -Dtest=DeckReadCqrsBoundaryAcceptanceTest test` — 11/11 |
| P4.1 Slice plan | Done | One AFK config slice; init does not auto-wipe production volumes |
| P4.2 Primary evidence | Done | Red/green architecture acceptance |
| P4.3 Implementation | Done | Compose Redis flags, `init-cluster.sh`, local marker |
| P4.4 Test levels | Done | Config/architecture acceptance selected; unit/component/IT N/A — no Java behaviour change |
| P4.5 Error path | Done | Init fails closed on `:0`/`noaddr` instead of starting Deck Read |
| P4.6 Review | Done | Self-review after context break; no independent reviewer |
| P4.7 Targeted defect review | Done | No additional confirmed defect in the changed surface |
| P4.8 Quality gates | Done | Focused test and `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P4.10 Combined feature review | Mode-omit | One-slice bug-fix mode |
| P5.1 Build | N/A | No Deck Read image rebuild; Compose/config only |
| P5.2 Security scan | N/A | No dependency or executable-code change; production ready-marker remains required |
| P5.3 Deploy | Done | Local Redis volumes wiped once; cluster + Deck Read recreated |
| P5.4 Smoke | Done | Health `UP`/`PONG`; unauthenticated v2 is `401` not `503` |
| P5.5 Rollback | Done | Restore prior Redis command, init script, and local marker |
| P5.6 Monitoring | Blocked | Authenticated Discover observation has not started |
| P5.7 Docs | Done | This bug record |
| P5.8 Open risks/blockers | Done | No in-scope blocker remains; authenticated UI check is user-owned smoke |

Rollback: restore the prior Compose Redis command, init script, and local marker. That restores the `:0` 503 after container IP churn.
