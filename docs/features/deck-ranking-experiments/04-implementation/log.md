# Implementation log

## Slice 1 — named scoring variants (2026-09-09)

**Design turn taken.** `ScoringStage` still calls the two-argument
`ScoringService.score(viewer, candidate)`, and the variant is resolved inside it rather
than once per deck. The alternative — resolving in `ScoringStage` and passing the variant
down — would have broken `ScoringStageTest`'s stubs for no measurable gain: variant
resolution is a map lookup while no experiment runs, and one SHA-256 per candidate while
one does, against a deck build that already makes an HTTP search of up to 2000 profiles.

**Defaults live in Java, not only YAML.** `ScoringProperties.defaultVariants()` seeds
`baseline` and `reciprocal`. An environment that has not picked up the new
`application.yml` keys — `src/test/resources/application-test.yml` among them — still
resolves `baseline` to the two original strategies. Had the fallback been "sum every
registered strategy", registering the two new reciprocal beans would have changed
scoring for everyone and destroyed the baseline.

**Unknown strategy bean fails at startup; unknown variant does not.** A typo in the
catalogue would silently drop a strategy and corrupt an experiment, so it throws while
wiring. An unknown *assigned* variant only means a stale experiment name, so it scores as
the default (NFR-6).

### Targeted defect review

| Lead | State | Evidence |
|---|---|---|
| `AgeCompatibilityStrategy` unboxes `candidate.age()`, a nullable `Integer` (R3) | **Fixed** | `AgeCompatibilityStrategyTest.scoresNeutralWhenCandidateAgeIsMissing` |
| `LocationProximityStrategy` unboxes `preferences().maxRange()` in `distance <= maxRange`; the record declares it `@NotNull` but `@JsonIgnoreProperties` deserialization does not enforce it | **Fixed** | `AgeCompatibilityStrategyTest.proximityFallsBackToTheDefaultRadiusWhenTheViewerRadiusIsMissing` |

Both are the same class of defect and were found by reading the strategies while writing
their mirrors, not by a failing test.

### Evidence

- `./mvnw -B test -Dtest='!*IntegrationTest'` — **97 passed, 0 failed**, including
  `DeckApplicationTests` (full Spring context, so the bean-name map injection is proven
  wired) and the untouched `ScoringStageTest`.
- Integration tests (`*IntegrationTest`, Testcontainers and Redis) were **not run**: no
  Docker runtime in this session. Not reported as passed.


## Slice 2 — experiment registry and admin API (2026-09-09)

**Why the registry is a database and not Redis.** Deck had no database at all — only
`spring-boot-starter-data-redis-reactive`. Redis was the free option, and it is the wrong
one: the deck cache is deliberately ephemeral and rebuildable, while losing an
experiment's salt makes every past variant assignment unrecoverable. Deck now owns a
`deck_db` schema following the existing `*_DB_*` convention, bootstrapped in
`init-databases.sh` and migrated by its own Flyway, exactly as `moderation` does.

**Blocking JDBC inside a reactive service.** The deck is WebFlux. Rather than introduce
R2DBC — a stack this repository uses nowhere, and which would still need JDBC for Flyway —
the registry is read into memory by `ActiveExperimentProvider` on a schedule, so the deck
build never touches the database. The admin controller offloads its blocking calls to
`Schedulers.boundedElastic()` instead of running them on an event-loop thread. Hikari is
capped at four connections with `initialization-fail-timeout: -1`, so an unreachable
database never blocks startup.

**One running experiment, enforced by the database.** `deck_experiment_single_running_idx`
is a partial unique index on `status = 'RUNNING'`. Scoring resolves exactly one experiment,
so a second one is refused by Postgres rather than resolved by row order.

**FR-9 lives in the store, not the controller.** `updateDraft` refuses anything that is not
a `DRAFT`. Changing a running experiment's salt would silently rewrite which variant every
past viewer was in, and that assignment is recomputed rather than stored.

### Targeted defect review

| Lead | State | Evidence |
|---|---|---|
| `@Valid` / `@NotBlank` / `@NotEmpty` on the admin request body were decorative: `spring-boot-starter-validation` was not on deck's classpath, so an empty split returned 200 | **Fixed** | Found by `ExperimentAdminControllerTest.rejectsAnExperimentWithoutVariants` failing with 200 instead of 400; starter added. Checked that no other deck controller or DTO carries validation annotations, so nothing else changes behaviour |

### Evidence

- `./mvnw -B test` — **203 passed, 0 failed**, the complete suite including every
  Testcontainers integration test. Docker was available in this session, so the row that
  slice 1 left `Blocked` is now closed.
- `ExperimentStoreIntegrationTest` — 9 tests against a real PostgreSQL 17 with Flyway
  applying `V1__deck_experiment.sql`, covering the single-running-experiment index and the
  draft-only edit rule.
- `bash -n docker/postgres/init-databases.sh` — passed.
- Not verified: the compose stack was not started, so `deck_db` creation and the grants
  have only been checked by shell syntax and by the equivalent `moderation_db` block they
  were copied from.


## Slice 3 — separate admin application (2026-09-09)

**NFR-3 needed no new security code.** `SecurityConfig` in the gateway already guards
`/api/v1/admin/**` with `hasRole("ADMIN")`, so the whole slice needed a *route*, not an
authorization rule. Two tests now pin that the registry route keeps the `/api/v1/admin`
prefix and grants zero quota to every non-admin tier, so the rate limiter can never
degrade into a second, weaker authorization rule.

**No framework.** The user-facing client is Angular, and mirroring it would have meant a
second Angular workspace for four operations. `keycloak-js` is a plain library, so the
admin app is Vite plus TypeScript with no framework at all — 31 kB of JavaScript, 10 kB
gzipped. Nothing in FR-6 asks for more.

**nginx proxies `/api/` to the gateway** so the browser stays on one origin. The
alternative was adding the admin origin to `gateway.cors.allowed-origins`, which would
have widened CORS for every route to serve one internal tool.

**Runtime configuration, not a build-time bake.** `public/config.json` is fetched at
startup and served `no-store`, so Keycloak settings change by replacing one file. The
Angular client bakes its equivalent into `environment.ts`; for an operator tool that has
to follow whichever environment it is pointed at, a rebuild per change is the wrong trade.

**The Keycloak client is separate.** `tinder-admin` is its own public client, created
idempotently by `configure-realm.sh`, so the admin app's redirect URIs never widen the
user-facing `tinder-client`. The script previously managed no clients at all; the new
block follows its existing create-then-verify-then-fail-loudly style.

**Operational note.** nginx resolves a `proxy_pass` host at startup, so the container
fails to boot if the gateway is not up yet. The compose service therefore waits on
`gateway: service_healthy`.

### Evidence

- `npm run build` — `tsc --noEmit` clean, Vite build succeeds, `config.json` lands in
  `dist/`.
- `docker build` — image builds; `nginx -t` inside it passes once `GATEWAY_URL` resolves,
  and the image contains the built assets plus `config.json`.
- `./mvnw -B test` in `services/gateway` — **12 passed, 0 failed**, including
  `GatewayApplicationTests`, which loads the configuration, so Spring Cloud Gateway parses
  the new route.
- Not verified: the end-to-end login → token → gateway → deck path was never exercised. It
  needs Keycloak, the gateway and deck running together, which this session did not start.
  `configure-realm.sh` was syntax-checked only, never run against a live Keycloak.


## Slice 4a — population generator (2026-09-09)

**Slice 4 was split.** The population and the latent decision model are pure and verifiable
offline; the swipe driver needs Keycloak, the gateway, deck and deck-read running together.
Building both at once would have produced one unreviewable lump, and everything R1 asks for
lives in the first half.

**The control population needed a correction.** FR-11 says every agent wants "the same range
and radius". Implemented literally as an identical *absolute* window — everyone wants 25..35
— that is not symmetric at all: acceptance would depend only on the candidate's age.
Symmetry requires an identical window *width around the agent's own age* plus an identical
radius. `TestControlPopulationIsSymmetric` pins it, and the generator reports exactly
0.0000 one-sided pairs at asymmetry 0.

**Patience turned out to be load-bearing.** Ranking order only reaches the outcome because
each agent goes through a bounded number of cards. Without that bound every viewer would
eventually see every candidate and the ranker would be irrelevant — the experiment would
measure nothing.

**A metric was measuring the wrong thing.** The first popularity check reported the
concentration of the appeal *parameter*: 0.17 for the top decile, barely above the 0.10 of a
flat population. But appeal enters the decision inside a logistic, so what matters is the
concentration of *received interest*. Replaced by `InterestConcentration`, which measures the
outcome and reports 0.34.

**Widening the appeal spread saturated.** Appeal is squashed into (0,1), so raising its
standard deviation from 0.8 to 2.0 moved the concentration only from 0.24 to 0.30. The
binding constraint was its weight in the decision, not its spread. Settled at a weight of
7.0, which lands near 0.35, and named the weights as constants with the target they were
tuned to.

**A quadratic sort was caught before it ran.** `quantile` used an insertion sort — fine on
test-sized slices, 10^10 operations on a hundred thousand agents. Replaced with
`sort.Float64s`; the standard library was never a dependency cost.

### Measured behaviour

100,000 profiles, seed 1, about 0.4 s per population:

| asymmetry | one-sided | mutual | interest, top decile |
|---|---|---|---|
| 0 | 0.0000 | 0.0282 | 0.3460 |
| 0.25 | 0.0207 | 0.0272 | 0.3411 |
| 0.5 | 0.0351 | 0.0258 | 0.3359 |
| 0.75 | 0.0449 | 0.0239 | 0.3294 |
| 1 | 0.0507 | 0.0229 | 0.3216 |

One-sided acceptance rises monotonically from exactly zero; mutual acceptance falls as it
does. Interest concentration holds near a third across the sweep, so asymmetry and the
popularity skew are not confounded — moving one does not quietly move the other.

### Residual assumption, recorded rather than hidden

The decision weights are constants chosen to produce that ~35% concentration. That level is
a judgement, not a measurement, and it is not swept: a steeper skew would make reciprocity
matter more. It is the same class of hazard R1 named, at a dimension NFR-7 does not cover.

### Evidence

- `go test ./...` — passed. 13 tests covering reproducibility, control-population symmetry,
  monotonic asymmetry, contract bounds on stated preferences, geographic clustering,
  probability bounds, mutual symmetry, and the three checks that the latent model lies
  outside the ranker's feature space.
- `go vet ./...` and `gofmt -l .` — clean.
- Live generation at 100,000 profiles across five asymmetry values, table above.


## Slice 4b — swipe driver (2026-09-09)

**The token question had a configuration answer, not an infrastructure one.** Both services
that resolve a viewer validate against settings that are already env-driven — deck-read's
`quarkus.oidc.token.issuer`, and `JWK_SET_URL` / `JWT_ISSUER` / `JWT_AUDIENCE` in swipes-go.
So the driver signs its own RS256 tokens and publishes a JWKS, and a bench deployment is
pointed at it by configuration. A hundred thousand viewers never become a hundred thousand
Keycloak users, and no code changed. It follows the guarded shape `swipes-go` already set
with `SWIPES_INTERNAL_ONLY_BENCHMARK`: not "turn validation off" but "in bench mode, trust
a different issuer".

**AWS Lambda was considered and rejected.** The driver is a long-running, connection-reusing
HTTP loop. On Lambda it would need VPC attachment to reach the cluster, would lose
connection reuse between invocations — putting a TLS handshake straight into the latency
this experiment compares — and would hit the fifteen-minute cap on what should be a steady
state. The repository already has the right shape: k6 and wrk run as pods beside the
services.

**Agents were never processes.** `Agent` is a row. One driver holds many viewers in
goroutines; the latent state belongs to a profile, not to a thread.

**Decisions are drawn per pair, not from a shared source.** `pairDraw` hashes the viewer id,
the candidate id and the run seed. Two runs of the same population therefore produce the
same swipes regardless of worker count or completion order — otherwise comparing two ranking
variants would also be comparing two different sets of coin flips.

**Unknown cards are counted, never defaulted.** A card outside the generated population has
no latent state. Deciding it would mean inventing a preference and defaulting it would
quietly bias the outcome, so it is counted in `unknown` and skipped.

### Targeted defect review

| Lead | State | Evidence |
|---|---|---|
| `TestDecisionsAreReproducible` reported zero likes, which looked like a dead decision function | **Not a defect — the test was wrong** | Probing the distribution showed the model is healthy: over a viewer's nearest 200 candidates the mean like probability is 0.204 and the best is 0.90. The test had fed 60 agents spread over twelve cities, so the nearest candidate was hundreds of kilometres away and the distance term correctly crushed everything. Fixed by giving the test a dense population |
| The JWKS server built into `drive` dies with the command, so a service configured against that URL loses its keys between runs | **Documented, not fixed** | `deck-sim jwks` writes a static document; the README now says to configure services from that and treat the in-run server as a convenience |

### Evidence

- `go test ./...` — passed. 13 population and decision tests plus 11 new ones covering
  token verification against the published JWKS, subject/issuer/audience claims, key
  persistence across restart, patience, bearer headers, 202 BUILDING retry, pagination,
  unknown cards, empty decks and reproducibility.
- End-to-end CLI run against a local stub: 300 viewers, 5,215 cards shown, 5,515 requests in
  1.2 s; `outcomes.jsonl` written with counts only; `signing-key.pem` persisted `0600`.
- Not verified: no run against the real stack. The stub served the same twenty distant
  profiles to every viewer, so its 2.2% like rate is an artifact of the stub, not a property
  of the model — the README says so explicitly.


## Slice 5 — outcome screen (2026-09-09)

**Consumer stayed ignorant of experiments.** It owns swipes and matches, so it is the only
service that can count them, but it returns per-viewer counts for a time window and nothing
more. Deck — which owns the salt — buckets them into arms. Teaching consumer about variants
would have put the same secret in two services.

**`swipe_records` could not answer the question.** It holds one row per pair with both
decisions and no timestamp, so it cannot say what happened *during* an experiment.
`swipe_events` can: one row per swipe, with `swiper_id`, `decision` and `created_at`. A
match counts for both profiles, so `match_event_outbox` is unioned across its two id
columns.

**The denominator deviates from FR-7, deliberately and visibly.** FR-7 says "matches per
100 built cards". Consumer does not know how many cards were built — that number lives in
the deck cache and in the simulator's `outcomes.jsonl`. The screen reports **matches per 100
swipes** instead: a swipe is exactly one card the viewer acted on, and it is fully
observable in the data that already exists. This changes an approved requirement and is
recorded as an open question rather than quietly substituted.

**Wilson, not the normal approximation.** Match rates are small, and the normal interval
misbehaves near zero — it can produce a lower bound below zero, which reads as nonsense on
a screen. The admin says in words when two arms' intervals overlap, so a promising-looking
average cannot be read as a result.

### Targeted defect review

| Lead | State | Evidence |
|---|---|---|
| `keepsTheIntervalAboveZeroWhenNothingMatched` failed on `isZero()` | **Not a defect — the assertion was wrong** | At p=0 the Wilson lower bound is exactly zero analytically, but centre and margin are computed separately and leave a rounding tail around 1e-18. The assertion now checks non-negativity and closeness to zero, which is the property that matters |
| The native SQL in `ExperimentOutcomeRepository` had no test, and a renamed column would break it silently | **Fixed** | `ExperimentOutcomeRepositoryIntegrationTest`, 5 tests against a real PostgreSQL, covering the window boundaries, both sides of a match, and a viewer with a match but no swipe in the window |

### Evidence

- `./mvnw -B test` in `services/deck` — **212 passed, 0 failed**, including the full
  Testcontainers set.
- `./mvnw -B test` in `services/consumer` — **50 passed, 0 failed**, including the new
  repository integration tests.
- `npm run build` in `clients/tinder-admin` — `tsc --noEmit` clean, bundle 32.9 kB.
- Not verified: deck has never actually called consumer's new endpoint. The mTLS hop is
  covered only by a mocked `ConsumerOutcomesHttp` in the controller test.


## Slice 5 correction — interval resampled over viewers (2026-09-10)

**The first interval was wrong in a way that would have manufactured results.** It was a
Wilson interval over swipes, which treats every swipe as an independent trial. Swipes by one
person are not independent: a picky viewer likes rarely across all of theirs, an appealing
one matches often across all of theirs. Using swipes as the sample size claims far more
evidence than exists, so the interval came out too narrow and would have called a difference
proven before it was — with NFR-9's "non-overlapping intervals" resting on it.

**Replaced with a bootstrap over viewers.** Resample whole viewers with replacement a
thousand times, recompute the pooled rate each time, take the 2.5th and 97.5th percentiles.
The unit of observation is now the person. It also settles how to weight a viewer with three
swipes against one with fifty — their swipes travel with them — which a simple mean of
per-viewer rates would have had to answer arbitrarily.

Seeded from the salt and the variant, so reloading the results page does not move the
interval.

**No data or contract changed.** `ViewerOutcome` already arrived one row per viewer; the old
aggregator summed that structure away immediately. The fix is local to `OutcomeAggregator`.

### What the tests caught

Writing them with arms of *identical* viewers made two fail: every resample of identical
people gives the same rate, so the interval had zero width no matter how many people were
in the arm. That is not a bug — it is what resampling over people means, and it is exactly
the difference from the swipe-level interval, which would have invented uncertainty there.
The fixtures now vary between viewers, and
`reportsNoUncertaintyWhenEveryViewerIsIdentical` pins the degenerate case on purpose so the
semantics are not mistaken for a defect later.

One viewer returns the full 0-100 range rather than a zero-width interval: resampling a
single person would otherwise report perfect certainty from one person.

### Evidence

- `./mvnw -B test` in `services/deck` — **216 passed, 0 failed**.
- Two of the new tests pin the defect directly: the same number of swipes spread over four
  people yields a wider interval than over forty, and more viewers narrow it.
- `npm run build` in `clients/tinder-admin` — clean; the results panel now states that the
  interval is resampled over viewers, not swipes.

### Cost

The bootstrap is a thousand passes over an arm, so it is linear in viewers. At bench sizes
this is milliseconds; at a hundred thousand viewers in one arm it would be a noticeable
fraction of a second on an admin request. Acceptable for an on-demand operator screen.


## Slice 6a — offline screen (2026-09-10)

**The concept's own off-switch was testable for free.** NFR-10 cancels the paid run if
`baseline` already reaches 95% of `oracle`. Headroom is a property of the population and the
ordering formulas, not of a deployment, so it can be estimated with no stack at all: for each
viewer, take the candidates near them, order them under a variant, and sum the true
mutual-like probability of the cards their patience lets them reach. Ranking changes only
which cards land in reach.

`random` and `oracle` were added as reference arms — the floor and the ceiling — because
without them the other two numbers have no scale.

### Result

100,000 profiles, 2,000 viewers per point, 93 seconds for the whole sweep. Matches per 100
swipes:

| asymmetry | random | baseline | reciprocal | oracle | baseline/max | reciprocal/max |
|---|---|---|---|---|---|---|
| 0.00 | 4.697 | 9.623 | 9.623 | 40.342 | 13.8% | 13.8% |
| 0.25 | 5.104 | 9.410 | 9.615 | 40.959 | 12.0% | 12.6% |
| 0.50 | 4.852 | 9.169 | 9.604 | 39.901 | 12.3% | 13.6% |
| 0.75 | 4.422 | 8.877 | 9.392 | 38.322 | 13.1% | 14.7% |
| 1.00 | 4.017 | 8.471 | 9.151 | 36.597 | 13.7% | 15.8% |

**The control passed.** At asymmetry 0 baseline and reciprocal are identical to nine decimal
places. The harness can produce "no difference" where there should be none, which is what
makes the other rows worth reading.

**NFR-10 does not fire.** Baseline reaches about 13% of the ceiling, not 95%. There is a
four-fold gap between both heuristics and the best achievable ranking.

**But NFR-9 is not met either.** Reciprocity's relative gain rises monotonically with
asymmetry — 0%, 2.2%, 4.7%, 5.8%, 8.0% — and tops out below the 10% the concept fixed as
the significance threshold before any run. Even at maximum preference disagreement the effect
under test does not clear its own pre-registered bar.

**Read together, these say the fix under test is not where the win is.** Both heuristics sit
near 13% of the ceiling; reciprocity moves that to at most 15.8%. The missing four fifths is
latent preference — taste and appeal — which no sum of an age curve and a distance curve can
express. That is the level-3 learned ranker, not this feature.

### What this means for slice 6b

The paid run would measure an 8% effect against a threshold of 10%. Spending the credits to
confirm a result the concept has already agreed does not count is the wrong trade. The
decision belongs to the owner and is recorded as Q9.

Lowering the threshold after seeing the numbers is the one option that is not available: it
was written down beforehand precisely so it could not be moved afterwards.

### Evidence

- `go test ./...` — passed, 35 tests. New ones pin the Java-mirrored curves and weights, that
  baseline cannot separate a reachable candidate from an unreachable one while reciprocal
  can, that the control population leaves the arms identical, that the arms order
  floor-to-ceiling, that reciprocity's advantage grows with asymmetry, and the headroom
  arithmetic including a zero span.
- `go vet` and `gofmt -l` — clean.
- Live sweep, table above.

### Known hazard

The ranking formulas are duplicated from the Java strategies. `TestRankingParityWithTheJavaStrategies`
pins the Go copy and documents the constants, but nothing executes the Java code, so a change
there would leave this screen silently measuring a ranking the service no longer uses.


## Slice 6b — cancelled by the owner (2026-09-10)

The paid run is not made. The offline screen put reciprocity's best relative gain at 8.0%
against a 10% threshold fixed before any run, and lowering that threshold after seeing the
numbers was never an option. No credits spent.

## Screening a learnable ranker (2026-09-10)

Before opening a new feature for the learned ranker, the same offline check that cancelled
6b was run on it — the discipline that had just saved the money.

The arm ranks by **observed popularity of the candidate × how well the pair fit each other's
stated preferences, in both directions**. It reads no latent state: popularity is a count the
platform already records, and the fits come from `SharedPreferencesDto`. The viewer's own
popularity is constant across their deck, so it cannot change the order and is left out.

Popularity is learned from a **baseline pass**, which is what a real training set is: a
candidate today's ranking never surfaced has no observed popularity, however wanted they
would have been. That exposure bias is inside the measured number, so the result below is
conservative.

| asymmetry | random | baseline | reciprocal | learned | oracle | learned share of ceiling |
|---|---|---|---|---|---|---|
| 0.00 | 4.697 | 9.623 | 9.623 | 16.979 | 40.342 | 34% |
| 0.25 | 5.104 | 9.410 | 9.615 | 17.232 | 40.959 | 34% |
| 0.50 | 4.852 | 9.169 | 9.604 | 16.956 | 39.901 | 35% |
| 0.75 | 4.422 | 8.877 | 9.392 | 16.453 | 38.322 | 35% |
| 1.00 | 4.017 | 8.471 | 9.151 | 15.836 | 36.597 | 36% |

**+76% matches over baseline**, against reciprocity's 8% and NFR-9's 10% bar.

Two further readings:

- The learned advantage is flat across asymmetry (34-36%) while reciprocity's grows. That
  fits: popularity is the dominant term in how people decide, and it does not depend on
  whether preferences disagree.
- It still leaves about two thirds of the gap. That remainder is latent taste, which no
  arithmetic over stated preferences and counts can reach — it needs something learned from
  co-swipe structure.

This is evidence for opening the level-3 feature, not part of it. The screen shares the
duplicated-formula hazard of R13.


## Screening the embedding ranker (2026-09-10)

The owner chose to go straight to embeddings. Screened first, on the same principle that had
just cancelled the paid run — and the screen argued against the choice, then explained why.

**First attempt was an unfair comparison, mine.** The embedding arm ranked on model output
alone while the popularity arm kept the age and distance terms. It scored 20% of the ceiling
against 33%. A model estimates taste, and taste is worthless for someone four hundred
kilometres outside the viewer's radius: the learned score replaces the popularity scalar, not
the feasibility constraints. Gated the same way, it came back to 31-34%.

**Still level with plain popularity — and the diagnostic said why.** 5.1 observations per
profile. A sixteen-dimensional vector fitted on five observations is fitting noise. So the
question became whether density, not model class, was the constraint.

| observations per profile | learned (popularity) | embedding (factorisation) |
|---|---|---|
| 5.3 | 34% of ceiling | 32% |
| 8.1 | 34% | 35% |
| 12.6 | 38% | 40% |
| 25.2 | 35% | 41% |

**The crossover is about eight observations per profile.** Below it the popularity scalar
wins outright and is vastly cheaper. Above it the factorisation pulls away.

And eight is a *floor*, not an estimate: the simulator scores taste as a dot product of
latent vectors and a factorisation estimates dot products of latent vectors, so this arm is
handed its own generative form. A real model, facing taste that is not a clean dot product,
would need more evidence than this, not less.

**What this implies for the plan.** Ship the popularity ranker first — it is arithmetic over
a counter deck can maintain from a stream it already consumes, and it captures most of what
is reachable at today's data density. Instrument likes-received per profile. Build the
factorisation when that number crosses roughly eight, not before. Going straight to
embeddings would spend weeks of ML work to be beaten by a `log(1+likes)` counter.

The owner's two other choices are already in: popularity enters as `log(1+likes)`, and both
learned arms reserve a share of every deck for profiles with no evidence. Time decay is *not*
evaluated here — a single-pass screen has no time axis, so that one stays a production design
decision this measurement cannot speak to.

### Defect found in my own tool

`screen` read the `-asymmetry` list before `flag.Parse`, so the flag was silently ignored and
every run swept the default five points. Two measurement runs were wasted before it showed
up. Fixed, with the ordering rule written next to it.

### Evidence

- `go test ./...` — passed, 35 tests.
- `go vet` and `gofmt -l` — clean.
- Four density points measured, table above.


## Measuring the newcomer quota (2026-09-10)

A single pass can only show what the quota costs — reserved cards are cards withheld from
the best-ranked candidate. What it buys appears only once the ranker trains on its own
output, so this needed rounds, which also gave the screen its first time axis.

**The first run said the quota did nothing at any size.** That was a defect in my own
implementation, not a finding: newcomers were appended to the tail of the deck on the
reasoning that the quota is exposure rather than promotion. With a 500-card window and a
viewer whose patience is about 25 cards, position 480 is never reached. A quota beyond the
viewer's reach is not a quota. Fixed by interleaving the reserved slots through the deck.

**Interleaved, the result reversed my expectation.** Six rounds, 20,000 profiles, 4,000
viewers per round:

| quota | matches per 100 swipes | counter coverage |
|---|---|---|
| 0% | 10.97 | 11.5% |
| 5% | 12.43 | 18.6% |
| 10% | 12.99 | 31.0% |
| 12% | 13.00 | 36.0% |
| 15% | 12.86 | 39.2% |
| 20% | 12.52 | 48.6% |

The quota does not cost matches, it produces them. Without one the counter knows eleven
percent of profiles, so the ranker is sorting mostly noise; showing newcomers buys evidence
faster than reserving positions costs placement. The cost only appears past 15%.

Ten and twelve percent are indistinguishable on matches, so twelve is taken: the counter
covers a fifth more profiles there.

**Granularity is bounded by patience.** A 2% quota changed nothing — one slot every fifty
cards, and no viewer reaches the fiftieth. Below roughly one over mean patience, a quota is
invisible however it is implemented.

### Evidence

- `go test ./...` — passed. Three new round-based tests: the quota widens coverage, a quota
  finer than the viewers' reach is inert, and coverage grows round on round.
- `go vet`, `gofmt -l` — clean.
- Two measured sweeps, tables above.


## Slice 3 verified against a live Keycloak (2026-09-10)

The realm bootstrap addition ran for the first time against a running Keycloak and reported
`PASS: Keycloak client 'tinder-admin' redirects to http://localhost:5173`, closing the
"syntax-checked only, never run" gap the slice was recorded with.

**It also surfaced a defect that predates this feature.** `SecurityConfig` guards
`/api/v1/admin/**` with `hasRole("ADMIN")`, and `KeycloakJwtAuthenticationConverter` maps a
realm role `X` to the authority `ROLE_X`. The realm has no role named `ADMIN`. It has
`USER_ADMIN`, which matches `USER_BASIC` and `USER_PREMIUM` — and
`KeycloakAuthSecurityServiceImpl` asks for `USER_BASIC` and `USER_PREMIUM` by those names but
for admin by the bare `ADMIN`.

So every path under `/api/v1/admin/**` was unreachable for every user in the realm, including
the `/api/v1/admin/deck/**` endpoints that existed long before this work.

An `ADMIN` realm role was created and assigned locally, which makes the deployed code work
without touching it. The alternative — changing `isAdmin()` to ask for `USER_ADMIN` — is
almost certainly what was intended, but it changes an authorization rule and belongs to the
owner. Recorded as R15.


## Live run against the running stack — a defect only a live run could find (2026-09-10)

**`swipe_events` has no writer, and two of my repositories read it.**

`ExperimentOutcomeRepository` (the outcome screen, slice 5 of the experiments feature) and
`ProfileSwipeCountsRepository` (the like-rate backfill, slice 3 here) both queried
`swipe_events`. Against the running stack that table holds **0 rows**, beside 12,056 in
`swipe_records` and 12,125 in `swipe_event_outbox`. Nothing in the consumer service writes
it: the only references anywhere are an unused `SwipeEvent` entity and my two queries.

**What it would have cost.** The outcome screen would have reported zero swipes, zero likes
and zero matches for every arm — which reads exactly like "the ranking change had no effect"
rather than like a broken query. The backfill would have restored nothing while reporting
success, leaving every profile at the base rate after a Redis loss.

**Why I picked the wrong table.** I read the schema, found a table whose columns were exactly
right — `swiper_id`, `swiped_id`, `decision`, `created_at` — and used it. I checked its
*shape* and never checked whether anything *fills* it. That is the same mistake as reading
the README's durability claim and not checking where it was implemented: verifying the
artifact instead of the behaviour.

**Fix.** Both queries now read `swipe_event_outbox`, the one table that carries a swipe, its
direction, its timestamp and its decision — the last inside the JSON payload rather than a
column. Integration tests were rewritten to insert the way the service does, and a new
`ProfileSwipeCountsRepositoryIntegrationTest` pins that the count is keyed on the profile
that was swiped *on*, not the one swiping.

Neither table shape nor test coverage would have caught this. Only running it did.
