# Discover cards overlap during swipe

Severity: **major** · State: **fixed** · Scope: Discover swipe stack

## Expected versus actual

Given a stacked Discover well, only the current profile’s identity may paint in that well. The waiting card behind it must not show a name or placeholder letter — at rest, during the drag, or in the moment before the next card becomes current.

Actual: the next card’s placeholder letter and name ghost through / around the current card (screenshot 2026-08-22: Erin with a large ghosted “e” and doubled name).

## Repro

1. Open Discover with at least two cards.
2. Look at the current card; pass or like.
3. The waiting card’s letter and name must not appear in the well.

## Root cause

The stack mounted a full `app-swipe-card` for `z2` (the next profile). The current card’s inner article is what moves; its host is transparent. As soon as the current card shifts, the next profile’s 86px letter and name paint in the same well. Promoting the outgoing card to a `leaving` layer did not stop that, because the overlapping identity was the waiting card, not the one that just left.

## Fix

Only the current card and a departing card mount `app-swipe-card`. The cards behind them are anonymous plates (no profile binding), so the next name and letter cannot paint. Card surfaces are opaque.

Regression: `discover.deck-v2.acceptance.spec.ts` — stacked deck mounts only the current profile; during a swipe it mounts current + leaving.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: visual overlap vs established Discover swipe |
| R.2 Project profile | Done | This bug file |
| R.3 Omitted phases | Done | P1–P3 Mode-omit |
| P1.1–P1.12, P2.1–P3.7, P4.10 | Mode-omit | Bug-fix mode |
| B.1 Repro | Done | Owner screenshot of ghosted “e” / doubled name; owner note that it is the next stacked card |
| B.2 Expected vs actual | Done | Above |
| B.3 Confirmed vs lead | Done | Confirmed from stack CSS + full `z2` swipe-card |
| B.4 Root-cause mechanism | Done | Waiting `z2` swipe-card paints identity under the moving current card |
| B.5 Contract inspected | Done | Client-only; no API change |
| B.6 Regression red | Done | `frontCards()` is only the current profile on a stacked deck |
| B.7 Smallest safe fix | Done | Anonymous stack plates; only z3/leaving mount swipe-card |
| B.8 Regression green | Done | Discover specs |
| P4.1–P4.9 | Done | Client-only slice; self-review |
| P5.1 Build | Done | Vitest of affected specs |
| P5.2 Security scan | N/A | UI stacking only |
| P5.3 Deploy | N/A | Not requested |
| P5.4 Smoke | Blocked | Image rebuilt `--no-deps tinder-client`; owner hard-reload of Discover still required |
| P5.5 Rollback | Done | Revert Discover/swipe-card stack plates |
| P5.6 Monitoring | N/A | Not a production deploy |
| P5.7 Docs | Done | This file |
| P5.8 Open-risk | Done | No blocker |
