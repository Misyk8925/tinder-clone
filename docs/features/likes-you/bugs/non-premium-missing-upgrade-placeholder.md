# Bug: non-premium Likes has no purchase placeholder

Severity: **major** · State: **fixed** · Scope: `clients/tinder-client` Likes (`/likes`)

## Expected versus actual

Given a signed-in user without `USER_PREMIUM`, opening Likes must show the blurred-cards purchase placeholder and an Upgrade control.

Actual: Likes only showed that placeholder when `GET /api/v1/swipes/liked-me` returned 403. An empty 200 showed “No Likes Yet”, so there was nothing to tap for premium.

## Repro

1. Sign in as a basic user.
2. Open Likes from the nav.
3. Confirm the Gold / Upgrade overlay is missing.

## Root cause

`LikesComponent` injected `KeycloakService` but never called `hasPremium()`. It treated only HTTP 403 as the upsell. Gateway/filter misses, empty lists, and non-403 errors all looked like “no likes”.

## Fix

Show the placeholder when `hasPremium()` is false, before calling liked-me. Keep 401/403 as a fallback. Overlay is explicitly clickable.

## Regression

`likes.component.spec.ts` — Given a non-premium user, when Likes opens and liked-me is empty, then the purchase placeholder and Upgrade control are shown. Red before the `hasPremium()` gate; green after.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: established Likes upsell was missing |
| R.2 Project profile | Done | Repo-local bug record; English; Vitest; local Compose |
| R.3 Omitted phases identified | Done | Full-feature-only rows listed below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Bug-fix does not run full-feature ceremony |
| B.1 Repro | Done | Non-premium Likes showed empty state without Upgrade |
| B.2 Expected vs actual | Done | Placeholder required; empty 200 hid it |
| B.3 Confirmed | Done | `load()` only set `forbidden` on HTTP 403 |
| B.4 Root cause | Done | Client never consulted `hasPremium()` |
| B.5 Contract | Done | HTTP liked-me still premium-gated; UI upsell restored |
| B.6 Regression red | Done | Spec found no `.upgrade-box` on empty liked-me |
| B.7 Smallest safe fix | Done | Client-side `hasPremium()` gate + clickable overlay |
| B.8 Regression green | Done | `npx vitest run src/app/features/likes/likes.component.spec.ts` |
| P4.1 Slice plan | Done | One AFK UI slice; risk was hiding real empty premium state |
| P4.2 Primary evidence | Done | Component test against empty liked-me |
| P4.3 Implementation | Done | `likes.component.ts` gate + overlay z-index |
| P4.4 Test levels | Done | Component UI selected. Unit/integration/contract/e2e N/A: client-only gate, no API change |
| P4.5 Error path | Done | 401/403 still open the same placeholder |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | No additional confirmed defect in this slice |
| P4.8 Quality gates | Done | Vitest + `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | Focused Vitest |
| P5.2 Security scan | N/A | UI gate only; still does not reveal liker identities |
| P5.3 Deploy | Done | Local `tinder-client` rebuild when Compose is used |
| P5.4 Smoke | Done | Spec proves Upgrade is present and navigates to `/profile` |
| P5.5 Rollback | Done | Restore 403-only `forbidden` handling |
| P5.6 Monitoring | Done | Not yet observed beyond the spec |
| P5.7 Docs | Done | This bug record |
| P5.8 Open-risk view | Done | No in-scope blocker |
