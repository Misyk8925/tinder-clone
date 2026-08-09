# Bug Hunt

Goal: find real, reproducible defects before they become production incidents — and track every one the same way, so severity and root cause are comparable across bugs instead of living in someone's head.

Two different activities share this gate, at two different cadences. Confusing them is the failure mode this file exists to prevent: running a full-codebase scan every session is expensive and noisy and trains everyone to ignore it; never running one lets rot accumulate silently until it surfaces as an incident.

## Two modes

| | Targeted review | Full audit |
|---|---|---|
| Scope | The current diff / PR, relative to main | The whole codebase |
| Cadence | Every slice, as part of phase 4's Review step; once more across the *whole feature's diff* when all slices are done, before moving to phase 5 — a normal, frequent gate either way | On explicit request, or every N iterations/releases — **never automatically every session** |
| Where findings live | The feature's Linear Project, label `bug`, attached to the Phase 4 milestone (not a separate one) | A standing "Bug Audits" Linear Project, label `bug` + `bug-audit`, filed under a Milestone for this run |
| Compared against | Nothing — it's this diff | The previous full-audit run — see "Baseline diff" below |

The two targeted-review passes catch different things. Per-slice review catches what's wrong within that slice. The whole-diff pass at the end of phase 4 catches what two individually-correct slices break when combined — a bug that no single slice's review could have seen, because it only exists in their interaction.

If the codebase doesn't have a "Bug Audits" Linear Project yet, create one the first time a full audit runs — it's a standing, cross-feature artifact, not something that belongs inside a single feature's folder. Decide the audit cadence once and record it in `docs/contracts/conventions.md` alongside the other project-wide conventions (e.g. "full audit before every production release" or "every 10 completed features") — don't re-decide it per feature.

## Every bug is a Linear issue, in this exact shape

Not a chat message, not a code comment saying `// TODO: this might break` — an issue, label `bug` plus one severity label, with four required fields in the description. Use `assets/templates/bug-issue.md`.

**Repro steps** — an exact sequence of actions or inputs that reproduces it, or a minimal failing test/input. "Sometimes happens under load" is not a repro step; it's a lead. If a suspected bug can't yet be reduced to something reproducible, don't force it into this format — file it as `bug-suspected` instead (lighter: symptom, when observed, hypothesis) and promote it to a full `bug` issue once repro is nailed down. Don't count `bug-suspected` issues in a "found N bugs" tally; they aren't confirmed yet.

**Root cause** — the mechanism, not the symptom. "Request times out" is a symptom. "The connection pool isn't released on the error path in `OrderService.charge()`, so it exhausts after ~50 failed payments" is a root cause.

**Severity** — pick one, consistently, so it means the same thing across every bug in the project. As a Linear label: `severity-blocker` / `severity-major` / `severity-minor` / `severity-cosmetic`.
- **Blocker** — data loss, security exposure, total outage, or anything that must not ship. Blocks the phase-5 gate.
- **Major** — broken functionality with no workaround; wrong behavior visible to users.
- **Minor** — broken functionality with a workaround, or an edge case most users won't hit.
- **Cosmetic** — doesn't affect correctness — text, formatting, logging noise.

**Affected scope** — which services, modules, endpoints, events, or tables, using the same vocabulary as `02-contracts/` (e.g. "HTTP `POST /orders`", "event `order.created.v1`", "table `orders`") so a bug and the contract it violates are easy to cross-reference.

## The fix gate: no regression test, no Done

An issue does not move to Done because the code changed. It moves to Done when:

1. A test exists that reproduces the original bug. Confirm it the same way you'd confirm any red-green cycle: run it against the pre-fix code and check it fails, then against the fix and check it passes. A bug you can't make a test fail on isn't fixed, it's unverified.
2. That test is tagged `@regression` — the same tag production incidents already use (phase 5) — so "why does this test exist" is always answerable by "it's guarding against bug X" without archaeology.
3. **Race conditions and concurrency bugs need a test that actually forces the interleaving.** A serial, happy-path unit test that happens to pass proves nothing about the racing code path. Concretely: multiple threads/goroutines/coroutines hitting the shared resource with a barrier or wait-group to force overlap, enough repeated iterations to raise the odds of catching non-determinism, and — where the language has one — a race detector run as part of confirming the fix (`go test -race`, ThreadSanitizer, Java's stress-test harnesses). A concurrency test that passed on the first try and was only run once hasn't demonstrated anything.

Link the regression test (file + test name) in a comment on the issue before closing it.

A bug that's real but genuinely won't be fixed now doesn't get force-closed either — downgrade it explicitly to a risk (`risk`/`risk-accepted`, with an owner and a review-by date, per `references/linear-integration.md`) instead of leaving the `bug` issue open forever or closing it without a fix. Silence isn't a valid end state for either kind of tracking.

## Baseline diff, on every full audit

A full audit isn't useful in isolation — the signal is in the delta from last time. Before reporting results:

1. Pull the previous audit's issues from its milestone (label `bug-audit`).
2. Compare against what this run found:
   - **New** — found this run, no match last run.
   - **Closed** — found last run, genuinely absent now. Re-check, don't just assume: does the old repro still reproduce? Is there a merged fix linked to it?
   - **Recurring** — matches (same root cause, same scope) an issue that was marked fixed in a previous run, but shows up again. This is a signal, not routine housekeeping: either the earlier fix was wrong or incomplete, or this run's finding is a false positive. **Flag it for manual review.** Don't auto-close it as an already-handled duplicate, and don't auto-reopen-and-refix it without saying so first — matching bugs across independent passes is itself a judgment call, and a wrong call here (silently closing a real regression, or silently "fixing" something that was never actually broken) is worse than surfacing the ambiguity to the user.

Report all three lists — that's the audit's output, not a single pass/fail verdict. Use `assets/templates/bug-audit-report.md`.

## Say what this pass found, not what's true of the codebase

An LLM bug hunt is heuristic and best-effort. It samples the codebase; it does not prove the absence of what it didn't find. Before running a full audit, and again when writing up its results: never write or imply "all bugs are found," "the codebase is now bug-free," or "N bugs fixed, all clear" — those are claims about the whole codebase that no single pass can support. Report like this instead:

> Full audit, pass #4 (2026-07-20): found 6 new issues (1 blocker, 2 major, 3 minor), 1 recurring (flagged — see BUG-142), 4 closed since pass #3. This reflects what this pass found, not a completeness guarantee.

The same discipline applies at smaller scale to targeted review — "found 2 bugs in this diff" is a fine thing to say; "this diff has no bugs" is not something a review pass can establish, only "no bugs found in this pass."

## Exit criteria

- Targeted, per slice: every bug found in this slice's diff is either fixed (with a regression test) or explicitly downgraded to a risk with an owner — nothing left as an unfiled comment.
- Targeted, whole-diff pass: run once after all slices pass, before phase 5 — same fixed-format rule, same fix gate.
- Full audit: the New/Recurring/Closed comparison is done and reported; recurring bugs are flagged for human review, not silently resolved either way.
- No open `blocker`-severity bug issue in this feature's affected scope at the phase-5 gate.

## No Linear available (fallback)

Targeted-review bugs are feature-scoped, so they fit the existing per-feature fallback: log each in the "Bugs" table in `00-state.md` (`assets/templates/state.fallback.md`), with the four-field body saved as `docs/features/<slug>/bugs/<bug-slug>.md` from `assets/templates/bug-issue.md`. Same fix gate — no regression test, no marking it fixed.

A full audit isn't feature-scoped, so it doesn't have a natural home in any single feature's folder even without Linear. Use a standing, repo-root location instead: `docs/bug-audits/<date>-pass-<n>/`, containing `report.md` (from `assets/templates/bug-audit-report.md`) and one file per bug (from `bug-issue.md`). The baseline diff compares against the previous `docs/bug-audits/<earlier-date>-pass-<n-1>/report.md` the same way it would compare against a previous Linear milestone — same three lists, same rule about recurring bugs needing manual review, not auto-resolution.
