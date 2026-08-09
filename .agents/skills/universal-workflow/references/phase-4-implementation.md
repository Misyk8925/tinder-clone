# Phase 4 — Implementation loop

Goal: make the scenarios pass, in small slices, without accumulating mess.

```
        ┌──────────────────────────────────────────────┐
        ↓                                              │
  plan → code → test → review → refactor → e2e ────────┘  (on any failure)
                                            │
                                            └→ all green → phase 5
```

Run the loop **per slice**, not once for the whole feature. A slice is the smallest change that makes at least one Gherkin scenario go from red to green end-to-end. Ten small green loops beat one big loop that has been red for three days.

## 1. Plan

Write or update `04-implementation/plan.md`:

- The slices, in dependency order, each named by the scenario it turns green.
- For the current slice: files to touch, new modules, migrations, config/secrets, and the risk you are most worried about.
- Migrations get their own line — they are the change you cannot casually roll back.
- The same cheapest-baseline principle from phase 1 applies at slice level: reuse what exists before adding a new module, library, or abstraction. If the slice needs something the architecture didn't already call for, that's worth a second look — either it was missing from phase 1, or it isn't actually needed.

Keep it short. A plan longer than the diff it describes is procrastination.

## 2. Code

- Follow the project's existing conventions over your own preferences. Read neighbouring files first.
- Contract first: generate or hand-write the types from `02-contracts/`, then fill in the logic. The contract is the source of truth; if the code wants to differ, the contract changes first (and that is a change the user should see).
- No commented-out code, no TODOs without an issue reference, no silent `catch {}`.
- Handle every error path the contract promised. That is what the error table was for.

## 3. Test

Three levels, all of them:

- **Unit** — pure logic, edge cases, boundaries. Fast, no I/O.
- **Integration** — real database, real broker (testcontainers or equivalent), real migrations. Mocks at this level hide exactly the bugs you are looking for.
- **Contract** — the API matches `02-contracts/` (schema validation, or a tool like Spring Cloud Contract / Pact).

Test the error paths, not only the happy ones. Aim for meaningful coverage of branches, not a coverage percentage.

Create `04-implementation/qa-metrics.md` from `assets/templates/qa-metrics.md` when the first slice starts. It is a compact evidence ledger, not a dashboard project: update the test-run, traceability, defect, and NFR rows as the relevant evidence exists. Also update the applicable evidence rows in project-root `docs/quality/metrics.md`, created from `assets/templates/quality-metrics.md` when the first feature needs it. Follow `references/qa-metrics.md`; an unavailable environment is **Blocked**, never silently counted as green.

Create project-root `docs/quality/gates.md` from `assets/templates/quality-gates.md` when the first feature needs deterministic quality gates. Run the applicable blocking gates for each slice and record the CI command/report. The gates cover contract compatibility, architecture boundaries, migration safety, Config/IaC policy, changed-code duplication and cognitive complexity, dependency/license policy, and performance regression on affected hot paths. Dead-code analysis is advisory: triage its candidates instead of blocking a release on guesses. See `references/quality-gates.md`.

Use mutation testing selectively where it adds signal: changed pure domain logic, authorization, money calculations, validation, state transitions, or critical transformations. Record the selected module, surviving non-equivalent mutants, and resulting action in `qa-metrics.md`. Do not run it indiscriminately across generated code, I/O wrappers, or the whole repository merely to produce a score.

For changed UI, data, or asynchronous flows, include the matching quality evidence in the project-level document: accessibility checks for critical user paths, reconciliation/idempotency checks for data and events, and flaky-test rate for the affected suite. These are conditional on the change; they must be recorded as `N/A` with a reason when they do not apply.

## 4. Review

Review your own diff as if someone else wrote it, and report findings to the user rather than quietly fixing everything. This is also the **targeted bug hunt** — see `references/bug-hunt.md` for the fixed output format any confirmed defect must use; this checklist is what you're hunting against:

- Does it actually satisfy the FR, or only the test?
- Contract drift: does the implementation still match phase 2?
- Security: authz on every entry point, input validation, no injection, secrets not in code, no PII in logs, tenant isolation enforced.
- Failure modes: timeouts, retries, idempotency, what happens when the dependency is down.
- Concurrency: races, transactions, isolation level.
- Observability: can you debug this in production from the logs and metrics it emits?
- Readability: would you understand this in six months?

Not every finding is the same kind of thing:

- **A confirmed defect** — the code does something wrong, not just something you'd have done differently — is a **bug**: file it per `references/bug-hunt.md` (repro steps, root cause, severity, affected scope) and either fix it now with a regression test, or downgrade it explicitly to `risk-accepted` if it genuinely won't be fixed in this slice. It does not get silently absorbed into the diff either way.
- **A deliberate tradeoff** — "acceptable for now, tenant isolation on this admin-only endpoint is enforced at the gateway, not here" — isn't a bug, it's a decision: log it straight as a Linear `risk` issue (`risk-mitigated` if something was done to reduce it, `risk-accepted` if it's being knowingly carried, with an owner). Don't let "the user said it's fine" live only in chat history.

## 5. Refactor

Only with tests green. Remove duplication, name things properly, split what grew too big, delete dead code. Behaviour must not change — if a test needs updating during refactoring, it was not a refactoring.

## 6. E2E

Run the full Gherkin suite against a running system (docker-compose or an ephemeral environment), not against mocks. Plus the NFR checks that can be automated: a small load test for latency NFRs, a check that data lands where NFR-3 says it should.

## 7. Repeat or exit

**Any red step sends you back to plan.** Not back to code — back to plan, because a failure often means the slice was wrong, not just the line. Record it in `04-implementation/log.md`:

```
## 2026-07-12 — slice 3 (quote PDF)
Failed: e2e, PDF rendering timed out at 12 s (NFR-1 = 5 s).
Cause: Puppeteer cold start per request.
Change to plan: keep a warm browser pool; re-slice into 3a (pool) and 3b (render).
```

That log is the most valuable file in the folder when someone asks "why is it built like that?". Not every failed pass belongs in Linear as a risk, though — a test that goes red and gets fixed within the same loop is normal TDD churn, not a risk. Only what you're consciously choosing not to fully resolve right now earns an issue.

## Exit criteria

- Every phase-3 scenario passes.
- Unit + integration + contract + e2e suites green in CI.
- Review findings are either fixed (bugs, with a regression test) or explicitly logged with a status and an owner (bugs downgraded to `risk-accepted`; tradeoffs as `risk-mitigated`/`risk-accepted`) — nothing left as an unrecorded verbal "yeah that's fine".
- Automatable NFRs are verified.
- `qa-metrics.md` shows the current evidence: every release-scope FR/error path is traceable to a scenario, every release-scope NFR has a result or an explicit Blocked/Not applicable reason, and test results distinguish passed, failed, skipped, and blocked work.
- Every applicable blocking quality gate passed. A changed-code duplication or complexity exception has an explicit reason, owner, expiry/review date, and approved `risk` issue; dead-code findings are triaged rather than silently ignored.
- A changed schema has passed migration safety checks; a changed hot path has reproducible performance evidence against its baseline and NFR; a changed configuration or infrastructure manifest has passed its policy checks. Any unavailable gate is explicitly Blocked and decided as scope, risk, or work — never counted as green.
- One final targeted review across the *whole feature's diff* (all slices combined, not just the last one) — per-slice review catches what's wrong within a slice, not what two individually-fine slices break when combined. See `references/bug-hunt.md`.
- Repo artifacts (`plan.md`, `log.md`) reflect the final state; move to phase 5.
