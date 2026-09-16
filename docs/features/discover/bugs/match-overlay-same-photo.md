# Match overlay showed one person twice

Severity: **major** · State: **fixed** · Scope: Discover match overlay (design-preview fixtures)

## Expected versus actual

Given a like that becomes a match, the overlay must show **two people**: the current user and the liked profile.

Actual: both circular photos were Mila. It looked like the user liked themselves.

## Repro

1. `npm run start:preview`
2. Like Mila on Discover.
3. Overlay “It's a match” used the same `mila-discover.png` on the left and the right.

## Root cause

`INITIAL_MY_PROFILE` in `design-preview.interceptor.ts` reused `/assets/profiles/mila-discover.png` for Michael. The overlay bound `myPhotoUrl` and `matchedProfile.photos[0].url` correctly; the fixture made those URLs identical.

## Fix

Give preview-me `/assets/profiles/michael-preview.png`. Mila keeps `mila-discover.png`.

Regression: `design-preview.interceptor.spec.ts` — `/me` photo is not the deck card photo. `discover.match.spec.ts` — overlay `img` sources are `/me.png` and `/them.png`.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: overlay faces vs established two-person match |
| R.2 Project profile | Done | This bug file |
| R.3 Omitted phases | Done | P1–P3 Mode-omit |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Bug-fix mode |
| B.1 Repro | Done | Owner: overlay looked like a self-like; prior walkthrough screenshot |
| B.2 Expected vs actual | Done | Above |
| B.3 Confirmed vs lead | Done | Confirmed: both URLs were `mila-discover.png` |
| B.4 Root-cause mechanism | Done | Preview `me` photo aliased Mila’s asset |
| B.5 Contract inspected | Done | Client fixture only; no API change |
| B.6 Regression red | Done | `/me` photo must not equal deck photo |
| B.7 Smallest safe fix | Done | Distinct `michael-preview.png` |
| B.8 Regression green | Done | Interceptor + Discover match specs |
| P4.1 Slice plan | Done | Fixture photo + overlay src assertion |
| P4.2 Primary evidence | Done | Interceptor scenario |
| P4.3 Implementation | Done | interceptor + asset |
| P4.4 Test levels | Done | Component + interceptor; live AWS N/A (origin 522) |
| P4.5 Error paths | N/A | Photo URL fixture, no new failure path |
| P4.6 Fresh-context review | Done | Self-review after adding the regression spec |
| P4.7 Targeted defect review | Done | This bug, now fixed |
| P4.8 Quality gates | Done | Discover + interceptor tests |
| P4.9 Handoff | N/A | Same context |
| P5.1 Build | Done | Client unit tests |
| P5.2 Security scan | N/A | Fixture asset only |
| P5.3 Deploy | N/A | Live origin Cloudflare 522; no AWS CLI in this environment |
| P5.4 Smoke | Done | Preview like Mila → two different faces |
| P5.5 Rollback | Done | Revert fixture URL + asset |
| P5.6 Monitoring | N/A | Not a production deploy |
| P5.7 Docs | Done | This file |
| P5.8 Open-risk | Done | No blocker |
