# Release checklist — deck-popularity-ranker

Environment: the local Compose stack (`docker-compose.yml` + `docker-compose.local.yml`).
Not the VPS. Date: 2026-09-10.

| Step | State | Evidence |
|---|---|---|
| P5.1 Build / validation | **Done** | `deck` 245 tests, `consumer` 50, `gateway` 5, `deck-sim` 35, `tinder-admin` builds — all green |
| P5.2 Security scan | **Done** | Below |
| P5.3 Deploy | **Done** | Postgres, Keycloak, Redis, Kafka and `deck` running locally; Flyway applied `V1__deck_experiment.sql` |
| P5.4 Smoke | **Done** | Below |
| P5.5 Rollback | **Done** | Below |
| P5.6 Monitoring | **Not yet observed** | The counters have three events in them. Nothing has run long enough to observe |
| P5.7 Docs | **Done** | Concept, plan, log, this checklist, `deck-sim/README.md` |
| P5.8 Open risk / blockers | **Blocked** | R15 is open and belongs to the owner — see below |

## P5.2 — security scan of the changed surface

- Every new HTTP path is either under `/api/v1/admin/**`, which the gateway guards with the
  ADMIN role, or under `/internal/**` on consumer's mTLS surface, restricted to
  `INTERNAL_CLIENT`. No new public path was added.
- `.env` is gitignored and holds no staged changes. The generated secrets are on disk only.
- No hardcoded password, secret or key appears in the new Java, TypeScript or Go.
- **The simulator's issuer signs a token for any subject** (R11). It exists only in
  `services/deck/load-tests/deck-sim`, its key is written `0600`, and trusting it outside a
  disposable bench is a full authentication bypass. It is not part of this deployment.

## P5.4 — smoke on the running stack

| Check | Result |
|---|---|
| Flyway applies the migration to a fresh `deck_db` | `Successfully applied 1 migration ... now at version v1`; `deck_experiment` and `deck_experiment_variant` exist |
| The variant catalogue resolves | `Scoring variants resolved: [baseline, popularity, reciprocal], default 'baseline'` |
| Density on empty counters | `profiles 0, likes 0, readyForEmbeddings false` |
| Create and start an experiment | `RUNNING` with a `startedAt` |
| A second experiment cannot run at once | `409` |
| A running experiment cannot be edited (FR-9) | `409` |
| Three swipe events raise the counters (FR-1, FR-2) | `impressions 3, likes 2` for the swiped profile |
| Density reports the embedding trigger (FR-7) | `likesPerProfile 2.0` against a threshold of `8.0`, `readyForEmbeddings false` |
| The Keycloak client bootstrap runs | `PASS: Keycloak client 'tinder-admin' redirects to http://localhost:5173` |

Not smoked: the admin app through the gateway end to end, and the deck-to-consumer mTLS
call. Both need the gateway and consumer running, which this deployment did not start.

## P5.5 — rollback

Set `deck.scoring.default-variant` to `baseline` and stop any running experiment; every
viewer then scores exactly as before, because `baseline` reproduces the pre-feature sum. The
counters keep filling harmlessly — nothing reads them under `baseline`.

To remove the feature entirely: revert the code, and drop the two Redis hashes
`deck:likerate:shows` and `deck:likerate:likes` plus the two total keys. The
`deck_experiment` tables belong to the previous feature and are not part of this rollback.

## P5.8 — what blocks a real release

**R15, and it is not this feature's defect.** The gateway guards `/api/v1/admin/**` with
`hasRole("ADMIN")`, which requires a realm role named `ADMIN`. The realm has `USER_ADMIN`,
matching `USER_BASIC` and `USER_PREMIUM` — and `KeycloakAuthSecurityServiceImpl` asks for
those two by their `USER_` names but for admin by the bare `ADMIN`. Every admin path was
therefore unreachable for every user, including `/api/v1/admin/deck/**`, which predates all
of this.

An `ADMIN` realm role was created and assigned locally so the deployed code works unchanged.
The alternative — changing `isAdmin()` to `USER_ADMIN` — is very likely what was intended,
but it changes an authorization rule and needs the owner.

Until that is settled, the admin surface works only where someone has been given the
`ADMIN` role by hand.

## Environment work done to get here

- Nine missing variables were written to `.env`: `DECK_DB_PASSWORD` (this feature's), plus
  the `MODERATION_*` and `PHOTOS_INTERNAL_AUTH_SECRET` values Compose has required since
  those features landed, and `ADMIN_APP_ORIGIN`.
- **Bcrypt hashes had to be escaped.** Docker Compose interpolates `.env` values, so a
  literal `$` in a bcrypt hash reads as a variable reference and the hash reaches the
  container mangled. Proven with an isolated probe: unescaped `$2b$10$abcDEF.ghiJKL` arrives
  as `$2b$10.ghiJKL`. The two hashes are written with `$$`.
- `deck_db`, the `deck_app` role and its grants were created by hand. The Postgres volume
  already existed, so `init-databases.sh` never re-ran — exactly the gap recorded in the
  previous feature's state file. `moderation_db` has the same gap and is still missing.


## Live run against the running stack (2026-09-10)

The stack was brought up locally — Postgres, Keycloak, Redis, Kafka, profiles, deck,
deck-read, swipes, consumer, gateway — and 20,000 generated profiles were seeded into
`profiles_db`. Two hundred of them were given real Keycloak users so the driver could
authenticate as them.

| Check | Result |
|---|---|
| A real token from Keycloak reaches deck-read | `GET /api/v2/deck` returns `202 BUILDING` — the viewer resolved |
| A real swipe through the gateway is accepted | `POST /api/v1/swipes` → `202` |
| That swipe reaches deck's like-rate counter | counters moved from `1 profile / 2 likes` to `2 / 3` |
| Consumer records it through the outbox | `Swipe outbox publish cycle: claimed=1, published=1` |
| **Backfill rebuilds from real history (FR-6)** | **4,042 profiles, 12,125 impressions, 6,183 likes** — impressions match the outbox row count exactly |
| Density reports the embedding trigger (FR-7) | `likesPerProfile 1.53` against a threshold of `8.0` |

### Two defects the live run found, both mine

**`swipe_events` has no writer.** Both `ExperimentOutcomeRepository` and
`ProfileSwipeCountsRepository` read it. The table holds zero rows beside 12,125 in
`swipe_event_outbox`. The outcome screen would have shown zeros for every arm — reading as
"no effect" rather than as a broken query — and the backfill would have restored nothing
while reporting success. Both now read the outbox. Fixed, with integration tests rewritten
to insert the way the service does.

**The backfill exceeded WebClient's buffer.** Pages of 10,000 rows blew the 256 KB default
in-memory limit and the rebuild failed with `DataBufferLimitException` — after `clear()` had
already run, leaving the counters empty. The page is now 1,000 rows, about ninety kilobytes.
Raising the client's codec limit was the wrong fix: it is shared with the deck build's hot
path.

Neither would have been caught by a unit test, an integration test, or reading the schema.

### Not achieved

The 200-viewer simulation did not run. Decks stay in `202 BUILDING`: the profiles were
inserted straight into the database, so no `profile.created` events were ever published and
deck-read's projection has nothing for them. Seeding through the event path, or backfilling
the projection, is the missing step — it was never scoped into a slice, and it is what stands
between this stand and a real simulated run.
