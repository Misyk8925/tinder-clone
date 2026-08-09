# Workflow state: <feature-slug>

**Fallback only.** Use this file when this project has no Linear available — see `references/linear-integration.md`. When Linear is connected, this content lives there instead (milestones, Gate issues, `risk`/`question`/`bug` labeled issues, Project Updates) — don't maintain both.

Current phase: **1 — Concept**
Last updated: <date>

## Approvals

| Phase | Artifact | Approved by | Date |
|-------|----------|-------------|------|
| 1 — Concept | `01-concept.en.md`, `01-concept.ru.md` | — | — |
| 2 — Contracts | `02-contracts/` | — | — |
| 3 — Behaviour | `03-behaviour/` (soft) | — | — |
| 4 — Implementation | all scenarios green | — | — |
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

One row per confirmed bug — see `references/bug-hunt.md` for the full shape; the four required fields (repro steps, root cause, severity, affected scope) go in `assets/templates/bug-issue.md`, saved as `docs/features/<slug>/bugs/<bug-slug>.md`, and linked from here. Not closed until the regression-test checklist in that file is fully checked.

| ID | Severity | Title | Detail file | Status | Regression test |
|----|----------|-------|--------------|--------|-------------------|

A full-codebase audit isn't feature-scoped, so it doesn't live in this file at all — see the "No Linear" note in `references/bug-hunt.md`.

## Open questions

| # | Question | Status |
|---|----------|--------|

## Decisions log

| Date | Decision | Why |
|------|----------|-----|

## Next action

<what the next person — or the next session — should do first>
