# Targeted defect review

Goal: review the delivery's changed surface for reproducible defects and close confirmed bugs with regression evidence. This is not a full-codebase audit.

## Cadence

- Review every changed slice as part of Phase 4 Review.
- For full feature delivery, review the combined feature diff once after all slices pass.
- For a one-slice bug fix or compressed change, the slice review is final unless its risks justify another independent pass.

Report what the review found. “No defects found in this pass” is valid; “the diff has no bugs” is not something one review can prove.

## Bug lifecycle and evidence

Use these states consistently:

- **Lead** — a symptom, report, or plausible concern without reproducible evidence.
- **Confirmed** — reproducible evidence shows actual behaviour violating established expected behaviour.
- **Diagnosed** — the bug is confirmed and its root-cause mechanism is understood.
- **Fixed** — a faithful regression test fails on the broken version and passes on the fix.

Confirmation and diagnosis are separate so a real reproduced defect can be tracked honestly while root-cause investigation is still in progress.

## Defect record

Use `assets/templates/bug-issue.md` and record:

- exact repro steps or a minimal failing input/test;
- expected and actual behaviour;
- lifecycle state: lead, confirmed, diagnosed, or fixed;
- root cause as a mechanism, not a symptom, or `unknown — diagnosis in progress` for a confirmed but not yet diagnosed bug;
- severity: blocker, major, minor, or cosmetic;
- affected services, modules, endpoints, events, or tables.

A suspected issue without reproducible evidence remains a lead, not a confirmed bug. Do not mark a bug diagnosed merely because one cause looks plausible.

## Fix gate

A fixed bug needs a regression test that fails on the broken version and passes on the fix. Follow the project grouping convention. If none exists, give the native-framework test a Gherkin-like Given/When/Then name and link it to the bug; a literal `@regression` tag is optional.

Concurrency defects need a test that forces the relevant interleaving and the language's race detector or stress tool when available. A serial happy-path test is not evidence for a race fix.

A confirmed bug that will not be fixed becomes an explicitly accepted risk with an owner and review date; it is not silently closed.

## Exit criteria

- Every confirmed defect found in the changed scope is fixed with regression evidence or explicitly accepted as an owned risk.
- No blocker remains open in the delivery scope.
- The review result and exact validation commands are recorded in the selected tracker or repo fallback.
- P4.6 and P4.7 are on the phase ledger. Self-review or "no confirmed defects in this pass" is an explicit row; a missing review row is a protocol failure.
