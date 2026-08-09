# Bug: <short title>

**Destination:** the description of a Linear issue, labels `bug` + severity. (No Linear: save as `docs/features/<slug>/bugs/<bug-slug>.md` — targeted review — or inside `docs/bug-audits/<date>-pass-<n>/` — full audit. See `references/bug-hunt.md`.)

Labels: `bug`, severity — one of `severity-blocker` / `severity-major` / `severity-minor` / `severity-cosmetic`
Found via: targeted review (diff/PR `<link>`) | full audit (`bug-audit`, run `<date/id>`)

If this can't yet be reduced to exact steps below, it isn't a `bug` yet — file it as `bug-suspected` instead (symptom, when observed, hypothesis) and promote it once repro is confirmed.

## Repro steps

1. ...
2. ...
3. ...

Expected: ...
Actual: ...

## Root cause

<the mechanism, not the symptom — what specifically causes this, not just what breaks>

## Severity

<Blocker | Major | Minor | Cosmetic> — <one line why this level and not the one above/below it>

## Affected scope

<services / modules / endpoints / events / tables — use the same names as `02-contracts/`, e.g. "HTTP POST /orders", "event order.created.v1", "table orders">

## Regression test

- [ ] Test written, tagged `@regression`
- [ ] Confirmed: fails on pre-fix code
- [ ] Confirmed: passes on post-fix code
- [ ] Concurrency/race bug only: test forces the actual interleaving (threads/goroutines + barrier, repeated iterations, race detector) — not a serial happy-path test

Test: `<file path / test name>`

**Do not move this issue to Done until every box above is checked.** If it's real but won't be fixed now, downgrade explicitly to a risk instead of leaving this open or closing it unfixed.
