# Implementation Iteration Log — swipe-rewind

## 2026-11-25 — Slice 2 (`swipes-go` config validation)
- **Failed:** `TestValidateAllowsExplicitBenchmarkConfiguration` failed with `rewind window must be positive` because existing benchmark struct-literal tests leave `RewindWindow` at its zero value.
- **Cause:** `Config.Validate()` rejected `RewindWindow <= 0` instead of `< 0`.
- **Change:** Updated `Config.Validate()` to reject only `< 0` (`WithRewind` defaults `0` to `5m`). Re-ran `go test -v ./...` → PASS.

## 2026-11-25 — Slice 5 (`deck-read` test compilation)
- **Failed:** `SwipeRewindDeckReadAcceptanceTest` failed to compile due to 5-arg vs 6-arg `DeckMaterializationRequest` constructor and `DeckCardDto.PreferencesDto` vs `DeckCardDto.Preferences`.
- **Cause:** Test helper signature mismatch against existing `DeckCardDto` / `DeckMaterializationRequest`.
- **Change:** Aligned test helper signatures; re-ran `SwipeRewindDeckReadAcceptanceTest,DeckMaterializationServiceTest` → 9/9 PASS.

## 2026-11-25 — Combined-diff fresh-context review (`P4.6` / `P4.7` / `P4.10`)
Independent fresh-context reviewer (`b6f98d69-8f51-4b54-b802-9b52618b76c1`) returned `CHANGES_REQUIRED` with 2 blockers, 1 major, and 4 minor findings:
1. **[Blocker, fixed]** `deck-read` `ViewerMutationStore.APPLY_FIRST_SWIPE_SCRIPT`: delayed `swipe-saved` (`swipeTs < rewoundAt`) ran `HSETNX`/`ZADD` before checking `dr:viewer:{id}:rewound`, re-hiding the rewound card. Moved `rewound` check above `HSETNX`/`ZADD` with early `return 0` + added regression test.
2. **[Blocker, fixed]** `consumer` `SwipeService`: (a) staleness check did not exclude `revokedSwipeEventId` so the revoked swipe's own outbox row could falsely trigger `IGNORED_STALE`; (b) `save()` did not check `swipe_revocations` when `SwipeRevoked` arrived before `SwipeCreated`. Replaced query with `existsBySwiperIdAndSwipedIdAndEventIdNotAndCreatedAtAfter`, added `existsByRevokedSwipeEventId` guard in `save()` + DB index in `V2_consumer_swipe_revocations.sql` + regression tests.
3. **[Major, fixed]** `swipes-go` `client/profiles.go` & `repository/profiles.go`: HTTP `404`/`403`/nil `profileId`/subject mismatch did not wrap `model.ErrProfileNotFound`, making `403 PROFILE_OWNERSHIP_DENIED` unreachable. Wrapped with `%w` + added `TestMineWrapsNotFoundAsErrProfileNotFound`.
4. **[Minor, fixed]** `deck-read` `SanitizedDeckReadDltSerializer`: added `SwipeRevokedEventV1` branch preserving non-PII replay UUIDs + regression test.
5. **[Minor, fixed]** `consumer` `SwipeService`: gated `ignored_match` / `ignored_stale` counter increments on `inserted > 0` + regression test.
6. **[Minor, fixed]** `docker-compose.yml` & migrations: aligned `swipe-revoked.dlt` partitions to 10, added `DECK_READ_SWIPE_REVOKED_GROUP_ID`, added idempotent role `GRANT` blocks in `V3_swipes_rewind.sql` and `V2_consumer_swipe_revocations.sql`.
7. **[Minor, fixed]** `tinder-client` `DiscoverComponent`: kept empty-deck Rewind button mounted (disabled) during `rewinding()` and cleared `matchWatchTimers` on rewind.
