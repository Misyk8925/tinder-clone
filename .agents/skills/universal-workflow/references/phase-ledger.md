# Phase ledger

Goal: the user can see every named sub-step of the active mode and phase, including the ones that were not done. **Silence is not a status.** A missing row is the same class of failure as skipping a hard gate.

Read this after `references/mode-router.md`. Keep the ledger in the user-visible reply; also copy the latest rows into the durable artifact for that mode (feature index / plan, compact issue, or bug item).

## Statuses

| Status | Meaning | Required on the row |
|---|---|---|
| `Done` | Completed for this change | Evidence: path, command, ticket, or cited decision |
| `N/A` | Does not apply to *this* change | Change-specific reason. "Optional" is not a reason |
| `Blocked` | Applies, but cannot be completed now | What is missing, and the next action |
| `Deferred` | Applies, delayed on purpose | When it will be done, and who agreed |
| `Mode-omit` | This mode does not run the step | Listed at routing; do not rediscover it later as a skip |

`N/A` does not drop a required decision or required evidence. `Blocked` is never counted as passed. `Deferred` without an owner agreement is `Blocked`. `Mode-omit` cannot keep compressed mode while dropping a required decision — promote instead.

## When to show it

Show the ledger, with non-`Done` rows first:

1. **After routing** — which phases and sub-steps this mode omits (`Mode-omit`) and which it will run.
2. **Whenever a row leaves `Done`** — the moment you mark `N/A`, `Blocked`, or `Deferred`, tell the user in that turn. Do not wait for the phase exit.
3. **At every phase or slice exit**, and at every hard gate. The phase-exit review in `references/phase-exit-review.md` uses this same table; do not keep a second copy.

If every row is `Done`, still show the compact table so the user can audit it. Do not replace the table with "skipped the optional parts" or "N/A where needed".

## Format

```
## Phase ledger — <mode> / <phase or slice>

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P1.3 Decision tree | Done | facts from `DeckQueryService`; 2 Proposed, 0 Open |
| P2.1 HTTP contracts | N/A | no HTTP surface in this change |
| P4.6 Fresh-context review | Blocked | no independent reviewer; self-review after context break |
```

One row per named sub-step below for the *active* phase. At routing, also list `Mode-omit` rows for phases the selected mode does not run. Templates show two or three sample rows so the shape is easy to check; copy IDs from this file, not from the template. Do not add extra ceremony rows; do not drop listed ones.

## Closed lists

These lists are the sub-parts. Inventing a shorter list to save space is a skip.

### After routing — all modes

| ID | Sub-step |
|---|---|
| R.1 | Selected mode and why |
| R.2 | Project profile resolved (tracker, language, contracts, tests, search, release) |
| R.3 | Phases and sub-steps this mode will not run, each as `Mode-omit` |

### `full-feature-delivery` — phase 1

| ID | Sub-step |
|---|---|
| P1.1 | Tracker container and phase milestones |
| P1.2 | Feature index linking tracker and canonical artifacts |
| P1.3 | Decision tree shown before concept/architecture (`Fact` / `Proposed` / `Open` / `Approved`) |
| P1.4 | Frontier interview, or explicit "no unresolved frontier" with cited sources |
| P1.5 | Category check: user, trigger/success, boundaries, numbers, integration, failure, stakeholders — each inferred, asked, or `N/A` |
| P1.6 | Wayfinder split, or `N/A` because one decision context is enough |
| P1.7 | Shared-understanding confirmation before the draft |
| P1.8 | Concept: problem, prose contracts, FRs, NFRs with numbers, out of scope |
| P1.9 | Suggested solution as cheapest baseline plus at least one rejected alternative |
| P1.10 | Diagram, or `N/A` because a single-service single-flow design does not need one |
| P1.11 | Self-check against the project and checkable claims |
| P1.12 | Phase-1 gate item created; stopped for explicit approval |

### `full-feature-delivery` — phase 2

Keep every boundary row even when unused.

| ID | Sub-step |
|---|---|
| P2.1 | HTTP canonical spec + readable view |
| P2.2 | Events canonical spec + readable view |
| P2.3 | Websocket canonical spec + readable view |
| P2.4 | Data: real migration + catalog/docs for changed columns/tables |
| P2.5 | Cross-cutting: authz, versioning/compatibility, rate limits, logging/PII |
| P2.6 | Error and edge cases on every affected boundary |
| P2.7 | Unresolved contract uncertainty recorded as owned risks |
| P2.8 | Continued to phase 3 without requesting a separate phase-2 approval |

### `full-feature-delivery` — phase 3

| ID | Sub-step |
|---|---|
| P3.1 | Project acceptance format chosen |
| P3.2 | Executable acceptance check for every FR |
| P3.3 | Executable acceptance check for every phase-2 error row |
| P3.4 | Traceability table filled from a literal pass, both directions |
| P3.5 | Combined phase-2/3 check: contracts parse, mirrors match, next slice red for the right reason |
| P3.6 | Unautomatable behaviour recorded as manual evidence or owned risk, never a fake test |
| P3.7 | Combined package shown; stopped for explicit approval |

### All delivery modes — phase 4 / implementation loop

Shorter modes use the same IDs in the compact issue. `Mode-omit` only what `references/mode-router.md` says that mode does not run.

| ID | Sub-step |
|---|---|
| P4.1 | Slice plan: observable result, dependencies, HITL/AFK, files, migrations, biggest risk |
| P4.2 | Primary evidence/red where the mode requires it |
| P4.3 | Implementation against approved contracts/behaviour |
| P4.4 | Each test level: unit, component, integration, contract, system/e2e, specialist — `Done` or `N/A` with the changed-risk reason |
| P4.5 | Error-path evidence for affected failures |
| P4.6 | Fresh-context review on specification fit and engineering fit, or explicit self-review |
| P4.7 | Targeted defect review with lead/confirmed/diagnosed/fixed recorded |
| P4.8 | Quality gates / qa-metrics rows that apply; unavailable checks `Blocked` |
| P4.9 | Session/agent handoff, or `N/A` because the same context continued |
| P4.10 | Combined-diff review for full features; `Mode-omit` for one-slice modes |

### Phase 5 / release evidence

Scale by mode as in `references/phase-5-release.md`. Unused full-release steps are `N/A` or `Mode-omit` with the mode reason, never dropped.

| ID | Sub-step |
|---|---|
| P5.1 | Build / validation of the affected artifact |
| P5.2 | Security scan of the affected surface |
| P5.3 | Deploy, or `N/A` because this change is not authorized/requested to deploy |
| P5.4 | Smoke / affected runtime checks |
| P5.5 | Rollback note or rehearsal when runtime behaviour can change |
| P5.6 | Monitoring / observation window, or `Not yet observed` |
| P5.7 | Docs/changelog/runbook that this mode requires |
| P5.8 | Open-risk view empty; no in-scope blocker bug |

### `bug-fix` — diagnosis path

Plus the phase-4 and affected phase-5 rows above. Do not add phase-1 concept rows; list them `Mode-omit` at routing.

| ID | Sub-step |
|---|---|
| B.1 | Repro, or explicit statement why blocked |
| B.2 | Expected vs actual, scope, severity |
| B.3 | Confirmed vs still a lead |
| B.4 | Root-cause mechanism |
| B.5 | Existing requirement/contract inspected; updated only if the boundary is wrong |
| B.6 | Regression check red on broken code |
| B.7 | Smallest safe fix |
| B.8 | Regression green on the fix |

### `compressed-small-change` — compact record

Plus the one-slice phase-4 rows and affected phase-5 rows. Phase 1–3 ceremony is `Mode-omit` at routing unless promotion is required.

| ID | Sub-step |
|---|---|
| C.1 | Intended outcome and out of scope |
| C.2 | Affected contract, or `none` |
| C.3 | Gherkin-like native acceptance check, or other declared observable evidence |
| C.4 | Selected test levels and exact commands |
| C.5 | Rollback note when runtime behaviour can change |
| C.6 | Promotion check: still one low-risk slice with no unresolved owner decision |

## Forbidden

- Completing a phase, slice, or gate request with a missing ledger row.
- Treating "the step is conditional" as permission to omit the row.
- Bundling several skips into one sentence.
- Marking review, tests, or release checks `Done` because they seemed unimportant.
- Using compressed mode to hide a skipped frontier, contract, or acceptance check.
