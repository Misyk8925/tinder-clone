# Implementation log

## Slice 1 — like-rate counters and the `popularity` variant (2026-09-10)

**Two counters, one existing handler.** `SwipeEventConsumer` already receives swiper, swiped
and decision, so the swiped profile's impression counter rises on every event and its like
counter on a right swipe. No new topic, no new consumer, no call to another service.

**Keys stayed out of the shared contract.** `DeckRedisKeys` exists for the deck hand-off to
deck-read, and nothing outside this service reads these counters, so widening that contract
would have shared something no other service needs.

**Scoring needed a way to receive per-deck data.** `ScoringStrategy.calculateScore(viewer,
candidate)` has nowhere to put a loaded rate table, and a strategy that fetched from Redis
itself would put one round trip per candidate on the build path — deck scores up to two
thousand candidates at a time. Solved with a defaulted contextual overload on the interface,
so the four original strategies are untouched, plus a `ScoringContext` carrying the rates.

**The pipeline already materialised the pool.** `ScoringStage` sorts its output, so nothing
streamed past that point anyway; collecting the list before scoring costs nothing and is what
makes a single batched lookup possible. The variant is now resolved once per deck rather than
once per candidate.

**The rate multiplies the fits, it does not add to them.** A match needs both sides, so the
quantity being maximised is a product of two chances, and a wildly popular profile four
hundred kilometres outside the viewer's radius is worth nothing — which a sum would not
express.

### Test fallout, all of it expected

Adding a collaborator to `ScoringStage` and `SwipeEventConsumer` broke four test classes that
construct them directly. `ScoringStageTest` and `DeckServiceTest` also stubbed the
two-argument `score`, which the stage no longer calls; those stubs became four-argument
matcher stubs. `ScoringServiceVariantTest` failed on the catalogue's fail-fast because its
fixed strategy map did not contain the new bean — the check working as designed, and the
fixture updated.

`DeckApplicationTests` passed throughout, so the Spring wiring was right from the first
compile.

### Evidence

- `./mvnw -B test` — **229 passed, 0 failed**, the whole suite including every Testcontainers
  integration test.
- `PopularityStrategyTest` — 7 tests: the more liked of two equally suitable candidates ranks
  higher; a popular candidate beyond either radius scores zero in both directions; an unseen
  candidate falls back to the base rate rather than zero; no context means the variant
  degrades instead of inventing a rate.
- `LikeRateStoreIntegrationTest` — 6 tests against a real Redis: impressions counted on every
  swipe and likes only on right ones, two lucky impressions not outranking two hundred proven
  ones, an unseen profile at the base rate, and a five-hundred candidate pool read in one
  call.

### Not done in this slice

The newcomer quota is configured (`deck.popularity.newcomer-quota: 0.12`) but not yet applied
to the deck build — that is slice 2. Until then a profile with no impressions is ranked at the
base rate, which keeps it out of the very bottom but does not guarantee it any exposure.


## Slice 2 — the newcomer quota (2026-09-10)

**The quota is part of the variant, not of the deck build.** Only rankings that use learned
rates reserve slots. A variant that does not is left exactly as it was — no Redis lookup at
all, which also means this feature costs the existing rankings nothing, and an A/B against
baseline compares one change rather than two.

Expressed as `ScoringStrategy.usesLikeRates()`, defaulted to false, so the four original
strategies stayed untouched again.

**"Unseen" is zero impressions, not a low rate.** A profile can land on the base rate by
coincidence; only a profile nobody has been shown qualifies for a reserved slot. The store
now returns that set alongside the rates rather than letting the caller infer it.

**Position had to be expressed in the score.** The deck is written to a Redis sorted set, so
promoting a profile by moving it up a Java list would have done nothing at all — it sinks
straight back to wherever its score puts it. Promoted profiles get a score interpolated
between the cards they should sit between; every other candidate keeps exactly the score the
strategies gave it, so the quota moves a few profiles instead of rewriting the deck.

That is the second time this feature's central mistake has been the same one: the first was
appending reserved slots past the viewer's reach in the simulator, and this is its production
equivalent. Both come from thinking about the list rather than about what the viewer sees.

### Evidence

- `./mvnw -B test` — **236 passed, 0 failed**, the whole suite including Testcontainers.
- `ScoringStageNewcomerQuotaTest` — 6 tests: a non-learned variant never calls the store at
  all; unseen profiles ranked bottom appear inside the first twenty cards; the deck stays
  ordered by score after promotion; no candidate is lost; an all-seen pool and a zero quota
  both change nothing.
- `LikeRateStoreIntegrationTest` gained a case pinning that a profile shown once and passed
  over is not a newcomer, however low its rate.


## Slice 3 — density and backfill (2026-09-10)

**The trigger for the next feature is now a number on an endpoint.** `GET
/api/v1/admin/popularity/density` reports profiles, impressions, likes, likes per profile and
whether that has crossed eight — the measured point where a factorisation of the swipe matrix
overtakes this counter. Exposing it is the whole reason the embedding work has a start
condition rather than an opinion.

Density is read from two running totals plus the hash size, not by walking every field: a
`HGETALL` over a hundred thousand profiles to answer one operator question would be absurd.

**The rebuild reads the durable history, because Kafka cannot.** The topic's retention is
shorter than the history, so a lost Redis cannot be replayed from it. Consumer's
`swipe_events` has every swipe ever recorded, and the new endpoint reads that same table down
the other column: what was done *to* each profile rather than what each viewer did.

**It clears before it writes.** Merging into whatever survived would double-count it, and a
counter wrong in an unknown direction is worse than one briefly empty — during which the
ranking degrades to the preference terms alone, which is safe.

**Paged by profile id.** A single response holding every profile that has ever been swiped on
would be a heap problem at both ends. One malformed id is logged and skipped rather than
aborting a recovery the operator is running because something already went wrong.

### Evidence

- `./mvnw -B test` in `services/deck` — **245 passed, 0 failed**.
- `./mvnw -B test` in `services/consumer` — **55 passed, 0 failed**.
- `LikeRateBackfillTest` — 4 tests: clears before writing, sums restored counts into the
  totals, skips an unreadable id without abandoning the rebuild, handles an empty history.
- `LikeRateStoreIntegrationTest` — now 12 tests, adding density arithmetic, the threshold
  answering both ways, and a restore that forgets what was there.
- `ProfileSwipeCountsRepositoryIntegrationTest` — 5 tests against a real PostgreSQL: counts
  what was done *to* a profile rather than what it did, and pages by id without repeating or
  skipping. The paging predicate casts a string parameter to `uuid`, which is exactly the
  kind of native SQL that passes a mock and fails on the first real page.
- Gateway route added and pinned by the existing route configuration tests.

### Feature status

Every FR and NFR in the concept is now built. Nothing has run against a live stack: the
counters, the rebuild and the density endpoint are covered by unit and Testcontainers tests
only.


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
