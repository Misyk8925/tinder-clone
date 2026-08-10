# Workflow state: <feature-slug>

**Fallback only.** Use this file when the project has no established tracker suitable for workflow state — see `references/project-profile.md`. Do not maintain it beside an authoritative tracker.

Current phase: **1 — Concept**
Last updated: <date>

## Approvals

| Phase | Artifact | Approved by | Date |
|-------|----------|-------------|------|
| 1 — Concept | concept document(s) required by the project profile | — | — |
| 2 — Contracts | `02-contracts/` (draft; no separate gate) | — | — |
| 3 — Contracts + behaviour | `02-contracts/` + `03-behaviour/` | pending | — |
| 4 — Implementation | all applicable acceptance and risk-selected checks green | — | — |
| 5 — Release | `05-release/checklist.md` | — | — |

## Pre-gate checks (phase 1 self-check loop)

<one or two lines: what was checked against the project, what claims were verified, what changed as a result — see phase-1-concept.md>

## Risk register

Any phase can add a row when it finds something. Status is one of:
- **Open** — identified, no decision yet. Never an acceptable state at the phase-5 gate.
- **Mitigated** — a concrete change reduced likelihood or impact (say what, and where — e.g. "moved to expand/contract migrations").
- **Accepted** — deliberately not mitigated further. Needs an owner and, ideally, a review-by date — this is a real residual risk, not a dropped one.
- **Closed** — no longer applicable (dependency removed, premise changed).

| ID | Raised in phase | Risk | Likelihood | Impact | Mitigation / plan | Status | Owner | Closed in phase |
|----|------------------|------|------------|--------|--------------------|--------|-------|------------------|

## Bugs (targeted review — this feature's diff only)

One row per confirmed bug — see `references/targeted-defect-review.md`; save the repo fallback from `assets/templates/bug-issue.md` under `docs/features/<slug>/bugs/` and link it here. Do not close it without regression evidence or explicit risk acceptance.

| ID | Severity | Title | Detail file | Status | Regression test |
|----|----------|-------|--------------|--------|-------------------|

## Open questions

| # | Question | Status |
|---|----------|--------|

## Decisions log

| Date | Decision | Why |
|------|----------|-----|

## Next action

<what the next person — or the next session — should do first>
