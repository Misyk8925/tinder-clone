# Phase-exit review

A short review session after each **run** phase. It is not a third hard gate. Owner approval stays where it already is: phase 1, and the combined phase-2+3 package.

Read this when leaving a phase. Use `references/phase-ledger.md` and that phase's exit criteria. Do not create a review document; keep the result in the user-visible reply and on the durable ledger.

## What it is

Check, in a context that did not just write the phase artifacts when the harness allows it:

1. Ledger complete — every named sub-step has a status; non-`Done` first.
2. Exit criteria hold, or the failure is sent back to the phase that could have prevented it.
3. Findings: defect, missing evidence, or an owner choice still labeled `Proposed`.

If no independent reviewer exists, self-review after a deliberate context break and say so. Same rule as P4.6.

## What it is not

- Not a new approve-the-phase gate after 2, 4, or 5.
- Not a second stop before an existing gate. Fold this review into the phase-1 request and into the combined 2+3 request.
- Not a substitute for per-slice review in phase 4.
- Not five sessions in `bug-fix` or `compressed-small-change`. Those modes run one exit review after their implementation loop (and affected release checks).

## Per phase

| After | Review session | Stops for owner? |
|---|---|---|
| Phase 1 | Ledger + concept exit criteria, then the existing gate request | Yes — that is the phase-1 gate |
| Phase 2 | Contracts ready to attach behaviour? Unused boundaries are `N/A`, not missing | No — continue to phase 3 in the same turn unless an owner decision appeared |
| Phase 3 | Combined 2+3 package; this review is the gate packet | Yes — the existing combined gate |
| Phase 4 | Per-slice reviews already ran; this session is the combined-diff review | Only if a finding needs a product/risk decision |
| Phase 5 | Ship evidence: Open-risk empty, no blocker, `Not yet observed` not counted as passed | Only if deploy was requested and a release decision remains |

Shorter modes: one session covering B.* or C.* plus applicable P4/P5. Do not invent phase-1–3 review sessions that the mode listed as `Mode-omit`.

## Format

```
## Phase-exit review — phase <n>

Verdict: pass | send back to phase <n> | blocked
Ledger: non-Done rows first (or “all Done”)
Finding: none, or one line each
Next: <gate request | continue to phase n+1 | stop for owner>
```

A pass with a missing ledger row is invalid. A fail that continues anyway is a skipped review.
