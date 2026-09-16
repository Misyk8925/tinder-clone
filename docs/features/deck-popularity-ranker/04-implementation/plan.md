# Implementation plan

| # | Slice | Observable result | State |
|---|---|---|---|
| 1 | Like-rate counters and the `popularity` variant | A deck ordered by how often each candidate is liked, behind the existing variant machinery | **Done** |
| 2 | Newcomer quota in the deck build | A measured share of every `popularity` deck goes to profiles with no impressions, interleaved so viewers actually reach them | **Done** |
| 3 | Density metric and counter backfill | FR-6 and FR-7: the embedding trigger becomes observable and a lost counter can be rebuilt | **Done** |

## Slice 1 — record

- **Observable result:** FR-1, FR-2, FR-3, FR-5.
- **Dependencies:** the experiment platform. **HITL/AFK:** AFK — every number came from the offline measurements.
- **Files:** `service/popularity/{LikeRateStore,PopularityProperties}.java`, `service/scoring/{ScoringContext,PopularityStrategy}.java`, `ScoringStrategy`, `ScoringService`, `ScoringStage`, `ScoringProperties`, `ScoringConfig`, `SwipeEventConsumer`, `application.yml`.
- **Migrations:** none — two Redis hashes, created on first write.
- **Biggest risk:** putting a Redis round trip per candidate on the deck build path. Avoided by loading the whole pool's rates in one batch before scoring; pinned by `LikeRateStoreIntegrationTest.readsAWholePoolInOneCall`.

## Slice 2 — record

- **Observable result:** FR-4.
- **Dependencies:** slice 1. **HITL/AFK:** AFK — the quota size came from measurement.
- **Files:** `ScoringStage`, `service/popularity/{LikeRateStore,LikeRates}.java`, `service/scoring/{ScoringContext,ScoringStrategy,PopularityStrategy}.java`, `ScoringService`.
- **Migrations:** none.
- **Biggest risk:** promoting a profile in list order while the deck is a Redis sorted set, so it sinks straight back. Position is therefore expressed in the score, and `keepsTheDeckOrderedByScoreAfterPromotingNewcomers` pins it.

## Slice 3 — record

- **Observable result:** FR-6 and FR-7. The feature's requirements are now all built.
- **Dependencies:** slices 1-2. **HITL/AFK:** AFK.
- **Files:** consumer `ProfileSwipeCounts{Controller,Repository}.java` and `ConsumerSecurityConfig`; deck `adapters/ProfileSwipeCountsHttp.java`, `service/popularity/LikeRateBackfill.java`, `LikeRateStore`, `experiment/PopularityAdminController.java`; gateway route.
- **Migrations:** none.
- **Biggest risk:** a rebuild that merges into a surviving partial counter and double-counts it. Avoided by clearing first, pinned by an ordering assertion.
