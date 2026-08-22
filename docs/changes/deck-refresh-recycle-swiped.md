# Deck refresh recycles already-swiped cards

## Compact scope record

- Intended outcome: after a viewer exhausts unseen suggestions, Refresh rebuilds the deck. New unseen cards come first; eligible already-swiped cards (PASS/LIKE, 7-day window, not matched) are appended. If there are no unseen cards, the deck is repeats only.
- Out of scope: changing `services/deck`, scoring, match recycling, a new client-visible fresh/repeat marker, or forcing recycle on ordinary pagination/polling.
- Affected contract: additive optional `refresh` query on `GET /api/v2/deck`. Omitted/`false` keeps the current read path. This amends FR-6: successful rebuilds now append repeats; the 30s/two-failure fallback is unchanged.
- Observable evidence:
  - Given unseen and eligible repeats, when a successful rebuild installs, then unseen IDs precede repeats and state is `READY`.
  - Given no unseen and eligible repeats, when a successful rebuild installs, then the snapshot is `DEGRADED` with repeats only.
  - Given a ready window of one fresh card plus one repeat, when the fresh card is swiped, then the Lua page still returns the repeat.
  - Given an authoritative empty page, when v2 is requested with `refresh=true`, then the client receives `202 BUILDING` and a rematerialization is requested.
  - Given Discover Refresh, when the deck is reloaded, then `refresh=true` is sent; polling and pagination omit it.
- Selected checks:
  - `mvn -B -Dtest=DeckSnapshotBuilderAcceptanceTest,DeckMaterializationServiceTest,DeckQueryServiceMaterializedAcceptanceTest,DeckRepeatFillTest,DeckRefreshTriggerTest,MaterializedDeckStoreAcceptanceTest test` from `services/deck-read`
  - `npx vitest run src/app/core/services/profile.service.deck-v2.acceptance.spec.ts src/app/features/discover/discover.deck-v2.acceptance.spec.ts` from `clients/tinder-client`
  - `ruby scripts/validate-deck-read-contracts.rb`
- Rollback: revert the deck-read materializer/snapshot/Lua `freshCount` changes and the optional `refresh` query; old generations without `freshCount` keep swipe-filtering every card.
- Promotion check: one reversible read-model policy plus an additive query flag. Owner requested the FR-6 amendment in-session; no further product fork.

## Evidence

- Deck-read mock acceptance: 23/23 passed
- `MaterializedDeckStoreAcceptanceTest`: 12/12 passed (Redis Testcontainers)
- Client Vitest: 6/6 passed
- Contract validator passed
- Compose rebuild of `deck-read-api` / `deck-read-worker` not run

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: one recycle-on-refresh slice |
| R.2 Project profile | Done | Compact `docs/changes` record; JUnit acceptance + Vitest |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Compressed mode |
| C.1 Outcome / out of scope | Done | Recorded above |
| C.2 Affected contract | Done | Additive `refresh` on GET `/api/v2/deck` |
| C.3 Acceptance check | Done | Snapshot builder, materialized store, query, client specs |
| C.4 Test levels and commands | Done | Commands and evidence above |
| C.5 Rollback | Done | Revert deck-read + optional query; missing `freshCount` is all-fresh |
| C.6 Promotion check | Done | Owner-requested FR-6 amendment; still one slice |
| P4.1 Slice plan | Done | Append repeats on successful install; Lua skip-swipe after `freshCount`; Refresh rebuilds |
| P4.2 Primary evidence | Done | New Given/When/Then tests listed above, red on old “never consult repeats” rule |
| P4.3 Implementation | Done | deck-read materializer/snapshot/Lua + Discover `refresh=true` |
| P4.4 Test levels | Done | Unit `DeckRepeatFillTest`; component/acceptance listed. Integration/e2e N/A: no live swipe session in this slice |
| P4.5 Error path | Done | Match still excludes repeats; repeat-ready false skips fill; Kafka refresh failure counted not fatal |
| P4.6 Review | Done | Explicit self-review after context break; no independent reviewer |
| P4.7 Targeted defect review | Done | No confirmed defects. Lead: first Discover open on EMPTY still needs Refresh to rebuild |
| P4.8 Quality gates | Done | Contract validator passed; deck-read targeted Maven passed |
| P4.9 Session handoff | N/A | Same context continued |
| P5.1 Build / validation | Done | Targeted Maven + Vitest + contract script |
| P5.2 Security scan | N/A | No new authz or secret surface; boolean query flag |
| P5.3 Deploy | N/A | Not requested |
| P5.4 Smoke | Blocked | Needs local rebuild of `deck-read-api` and `deck-read-worker` |
| P5.5 Rollback | Done | Revert this change; missing `freshCount` is treated as all-fresh |
| P5.6 Monitoring | N/A | Not yet observed; no deploy |
| P5.7 Docs | Done | Compact record + OpenAPI/FR-6 note |
| P5.8 Open-risk / blockers | Done | No in-scope blocker |
