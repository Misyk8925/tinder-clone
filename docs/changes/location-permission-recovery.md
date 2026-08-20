# Location permission recovery bug

## Status

Fixed locally on 2026-08-21. Production deployment and mobile-device smoke remain pending.

## Expected versus actual

- A user who explicitly selects **Continue without location** must reach Discover from every permission state.
- A temporary position timeout or unavailable result must remain retryable without being presented as a permission denial.

After one failed location attempt, the skip action navigated to Discover without persisting the skip flag. The Discover guard immediately redirected the user back to the permission screen. Every geolocation failure was also rendered as “Location is turned off.”

## Root cause and fix

`LocationPermissionComponent.skip()` intentionally omitted `markSkipped()` in the denied state, while `authGuard` requires either stored coordinates or that flag. The skip action now always persists the user's explicit choice before navigation.

`GeoLocationService.requestPermission()` now returns the native failure category. The component distinguishes permission denial from unsupported, unavailable, and timeout results while keeping retry and skip available.

## Regression evidence

- Component acceptance coverage proves denied-state skip persistence and temporary-failure recovery.
- Service tests prove success persistence plus denial and timeout classification.

Rollback is a code-only client rollback; no backend or persisted server data changes are involved.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| B.1 Reproduction | Done | Denied-state skip omitted the flag required by `authGuard`. |
| B.2 Expected/actual/scope/severity | Done | Blocker limited to client location onboarding recovery. |
| B.3 Confirmation | Done | Component regression test failed on the prior implementation. |
| B.4 Root cause | Done | Skip-state/guard invariant mismatch; native errors collapsed to denial. |
| B.5 Existing contract | Done | Backend and route destinations unchanged; internal service result made explicit. |
| B.6 Regression red | Done | Both recovery scenarios failed before the fix. |
| B.7 Smallest safe fix | Done | Persist explicit skip and classify native geolocation failures. |
| B.8 Regression green | Done | Focused and full client suites pass. |
| P4.1 Slice plan | Done | One AFK client slice; no migrations; mobile smoke remains HITL after deployment. |
| P4.2 Primary evidence | Done | Faithful component failures captured before implementation. |
| P4.3 Implementation | Done | Location service and permission component only. |
| P4.4 Test levels | Done | Unit/component: green; integration/contract/specialist: N/A, no affected external boundary; production e2e blocked until deployment. |
| P4.5 Error paths | Done | Permission denied, timeout, successful coordinates, and denied-state skip covered. |
| P4.6 Review | Done | Explicit self-review of the scoped diff and all `requestPermission()` consumers. |
| P4.7 Targeted defect review | Done | Reported blocker fixed; no additional confirmed neighbor defect. |
| P4.8 Quality gates | Done | 30 client tests, production build, and `git diff --check` pass. |
| P4.9 Handoff | N/A | Same context completed the slice. |
| P4.10 Combined review | Mode-omit | One-slice bug fix. |
| P5.1 Build | Done | Angular production build passes. |
| P5.2 Security scan | N/A | No dependency, authentication, authorization, or data-exposure change. |
| P5.3 Deploy | N/A | Production deployment was not requested or authorized. |
| P5.4 Smoke | Blocked | Requires deployment and a real mobile/browser location attempt. |
| P5.5 Rollback | Done | Revert the client-only change; no data rollback. |
| P5.6 Monitoring | Not yet observed | No deployed release exists for this fix. |
| P5.7 Docs | Done | This bug record contains behavior, cause, evidence, and rollback. |
| P5.8 Open blockers | Done | No remaining local engineering blocker; release evidence is explicitly blocked above. |
