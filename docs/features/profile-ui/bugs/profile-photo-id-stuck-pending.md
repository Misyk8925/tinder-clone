# Bug: Profile hero stays pending because GET /me uses `photoId`

Severity: **major** · State: **fixed** · Scope: Profile photo placeholders (`clients/tinder-client`)

## Expected versus actual

Given a signed-in user with uploaded photos, Profile must show the hero image once the browser has the bytes (or hide the skeleton on `error`).

Actual: the hero stayed a dark-gray placeholder (`2/5`, Manage photos still rendered). The `<img>` was in the DOM at opacity 0 and never marked ready.

## Repro

1. Local stack, profile with at least one photo.
2. Open `/profile` after the loading-placeholder change.
3. Hero remains empty gray for a long time (or indefinitely).

## Root cause

`PhotoDto` JSON field is `photoId`. The Angular `Photo` model and placeholder ready-set used `photoID`. `isPhotoReady(hero.photoID)` is always false for live GET `/me`, so the skeleton never clears even after `load`.

## Fix

Read `photoId` or `photoID` via `profilePhotoId()`. Ignore empty ids in `markPhotoReady`. Specs use the API field name.

## Regression

`profile.component.spec.ts` — Given getMe returns `photoId`, when the hero image loads, then the skeleton is gone and `data-photo-id` is `p1`. Red if the template still binds `photoID` only.

`profile-photo-merge.spec.ts` — Given Profiles JSON with `photoId`, when the client reads the photo, then that id is used.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: placeholders already exist; they never clear on live GET /me |
| R.2 Project profile | Done | Repo-local bug record; Vitest |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Bug-fix |
| B.1 Repro | Done | Screenshot of gray hero 2/5; code path `photoID` vs `photoId` |
| B.2 Expected vs actual, scope, severity | Done | This record |
| B.3 Confirmed vs lead | Done | Confirmed: API `PhotoDto.photoId`; ready-state used `photoID` |
| B.4 Root-cause mechanism | Done | Undefined id → `isPhotoReady` always false → opacity 0 + skeleton |
| B.5 Existing requirement/contract | Done | JSON field unchanged; client alias only |
| B.6 Regression check red | Done | Spec with API `photoId` would keep skeleton on `photoID`-only bind |
| B.7 Smallest safe fix | Done | `profilePhotoId()` |
| B.8 Regression green | Done | Vitest 7 passed |
| P4.1 Slice plan | Done | Alias both field names; keep placeholder behaviour |
| P4.2 Primary evidence | Done | Spec asserts `data-photo-id=p1` from `photoId` |
| P4.3 Implementation | Done | `profile.model.ts` + profile component + merge |
| P4.4 Test levels | Done | Component + unit. Integration/e2e N/A |
| P4.5 Error path | Done | Empty id ignored; img error still marks ready when id present |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | Delete now uses `photoId` as well (was undefined on live GET /me) |
| P4.8 Quality gates | Done | Vitest + `git diff --check` |
| P4.9 Handoff | N/A | Same context |
| P5.1 Build | Done | Focused Vitest |
| P5.2 Security scan | N/A | Field alias only |
| P5.3 Deploy | Done | Local tinder-client rebuild |
| P5.4 Smoke | Done | Specs |
| P5.5 Rollback | Done | Revert client photoId alias |
| P5.6 Monitoring | Done | Not yet observed live beyond specs |
| P5.7 Docs | Done | This record |
| P5.8 Open-risk view | Done | No in-scope blocker |
