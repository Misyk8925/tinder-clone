# Bug: <short title>

**Destination:** a bug item in the selected tracker. Repo fallback: `docs/features/<slug>/bugs/<bug-slug>.md`. See `references/targeted-defect-review.md`.

Labels: `bug`, severity — one of `severity-blocker` / `severity-major` / `severity-minor` / `severity-cosmetic`
Found via: targeted review of `<slice/diff/PR link>`

If this can't yet be reduced to exact steps below, it isn't a `bug` yet — file it as `bug-suspected` instead (symptom, when observed, hypothesis) and promote it once repro is confirmed.

## Repro steps

1. ...
2. ...
3. ...

Expected: ...
Actual: ...

## Lifecycle state

<Lead | Confirmed | Diagnosed | Fixed>

## Root cause

<the mechanism, not the symptom — what specifically causes this, not just what breaks; use `unknown — diagnosis in progress` until diagnosed>

## Severity

<Blocker | Major | Minor | Cosmetic> — <one line why this level and not the one above/below it>

## Affected scope

<services / modules / endpoints / events / tables — use the same names as `02-contracts/`, e.g. "HTTP POST /orders", "event order.created.v1", "table orders">

## Regression test

- [ ] Regression test written and linked; project tag/group used when available, otherwise a clear Gherkin-like native test name
- [ ] Confirmed: fails on pre-fix code
- [ ] Confirmed: passes on post-fix code
- [ ] Concurrency/race bug only: test forces the actual interleaving (threads/goroutines + barrier, repeated iterations, race detector) — not a serial happy-path test

Test: `<file path / test name>`

**Do not move this issue to Done until every box above is checked.** If it's real but won't be fixed now, downgrade explicitly to a risk instead of leaving this open or closing it unfixed.
