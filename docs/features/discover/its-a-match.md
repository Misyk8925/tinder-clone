# Compact change: Discover “It's a match”

Mode: `compressed-small-change`. Tracker: this file. Language: English (client copy).

## C.1 Outcome / out of scope

**Outcome:** After a successful right swipe, Discover polls `GET /match/{me}` a few times. If that list includes the swiped profile, show the existing overlay with both faces and a path into chat.

**Out of scope:** Changing the swipe HTTP contract (still `void`). Websocket match push. Ranking, Kafka, or AWS. The matching-observatory admin SPA.

## C.2 Contract

`none`. Client-only. Swipes-go still returns no body; match creation stays async via the existing match API.

## C.3 Acceptance

`clients/tinder-client/src/app/features/discover/discover.match.spec.ts`

- Given a successful like, when matches include that profile, then the overlay names them.
- Given a successful like, when the match is not in the first poll, then the overlay waits until it appears.
- Given a successful like, when matches belong to someone else, then Discover stays quiet.
- Given a pass, when the swipe succeeds, then matches are not polled.
- Given the overlay, when Send a message succeeds, then chat opens for that match.

## C.4 Test levels and commands

- Component / acceptance: `cd clients/tinder-client && npm test -- --watch=false --include=src/app/features/discover` (14 passed)
- Full client: `cd clients/tinder-client && npm test -- --watch=false` (99 passed)
- Production build: `cd clients/tinder-client && npm run build`
- Preview smoke: `npm run start:preview`, like Mila → overlay → Send a message; pass does not overlay; Matches still lists chats.
- Integration / Kafka / live two-account: N/A in this Cloud environment (Compose stack not started).

## C.5 Rollback

Revert the Discover component + spec. Overlay was already in the template; production ranking and swipe APIs are unchanged.

## C.6 Promotion check

Still one slice. No owner decision, no boundary change. Do not promote.

## Phase ledger — compressed-small-change / its-a-match

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | One Discover overlay slice; swipe API unchanged |
| R.2 Project profile | Done | GitHub PR tracker; Gherkin-like Vitest; client docs English |
| R.3 Mode-omit | Done | P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 ceremony omitted by compressed mode |
| P1.1–P3.7 | Mode-omit | Compressed mode |
| P4.10 Combined-diff review | Mode-omit | One-slice mode |
| C.1 Outcome / out of scope | Done | This file |
| C.2 Affected contract | N/A | none — existing `GET /match/{id}` |
| C.3 Acceptance check | Done | `discover.match.spec.ts` |
| C.4 Test levels + commands | Done | This file |
| C.5 Rollback | Done | Revert Discover files |
| C.6 Promotion check | Done | Still one low-risk slice |
| P4.1 Slice plan | Done | Overlay after like; files in Discover + demo script |
| P4.2 Primary evidence | Done | Acceptance spec |
| P4.3 Implementation | Done | `discover.component.ts` |
| P4.4 Test levels | Done | Component yes; integration N/A (no Kafka here); e2e = preview smoke |
| P4.5 Error paths | Done | Chat create failure → `/matches`; match poll errors ignored |
| P4.6 Fresh-context review | Done | Self-review after writing tests separately from overlay CSS |
| P4.7 Targeted defect review | Done | No confirmed defects. Leads: 3s poll may miss a slow live match; preview me photo is the same file as Mila so both overlay faces match |
| P4.8 Quality gates | Done | `npm test -- --watch=false` 99 passed; `npm run build` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | `npm run build` |
| P5.2 Security scan | N/A | No new authz surface; same match GET the Matches page already calls |
| P5.3 Deploy | N/A | Not authorized |
| P5.4 Smoke | Done | Preview like Mila → overlay → `/chat/preview-new-chat`; pass → no overlay |
| P5.5 Rollback note | Done | C.5 |
| P5.6 Monitoring | N/A | Not yet observed — no production deploy |
| P5.7 Docs | Done | `docs/demo/script.md` |
| P5.8 Open-risk | Done | 3s poll miss accepted; no blocker |
