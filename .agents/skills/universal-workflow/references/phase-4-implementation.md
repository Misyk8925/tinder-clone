# Phase 4 — Implementation loop

Goal: prove the requested outcome in small slices, using test levels selected by changed risk rather than by ritual.

Keep the P4 ledger from `references/phase-ledger.md` on every slice. Each test level and the review row is `Done` or `N/A`/`Blocked` with a reason. Omitting a level by not mentioning it is a protocol failure.

The artifact names below describe `full-feature-delivery`. In `bug-fix` or `compressed-small-change`, keep the same implementation discipline but store the compact plan and evidence in the mode's single issue or existing project location. Do not create an otherwise empty feature tree or QA document for one small slice.

```
        ┌──────────────────────────────────────────────────────────────────────────┐
        ↓                                                                          │
  plan → primary evidence/red where applicable → code + applicable tests → green/proven → review → refactor
                                                                                             │
                                                                                             └→ done → phase 5
```

Run the loop **per slice**, not once for the whole feature. For a full feature, a slice turns a phase-3 acceptance check green. For a bug, it turns the regression check green. A compressed mechanical change may instead produce the cheapest deterministic observable evidence. Ten small green loops beat one oversized loop.

Treat one fresh implementation context as a design constraint, not a token quota: the implementation session should normally be able to understand the approved inputs, implement the slice, run its tests, and report enough evidence for a separate fresh-context review. If that cannot be done without compacting away important decisions or loading unrelated parts of the system, re-slice before coding. Keep the slice vertical and observable; do not turn the split into separate database/API/UI layer tickets that cannot demonstrate behaviour on their own.

## 1. Plan

For full feature delivery, write or update `04-implementation/plan.md`. For the shorter modes, put the same applicable fields in their compact issue or plan entry:

- The slices, in dependency order, each named by the observable behaviour or scenario it turns green.
- For every slice: blocking dependencies, `HITL` or `AFK` mode, and the concrete result a reviewer can observe.
- For the current slice: files to touch, new modules, migrations, config/secrets, required observable evidence, changed risks, selected test levels, manual check (if any), and the risk you are most worried about.
- Migrations get their own line — they are the change you cannot casually roll back.
- The same cheapest-baseline principle from phase 1 applies at slice level: reuse what exists before adding a new module, library, or abstraction. If the slice needs something the architecture didn't already call for, that's worth a second look — either it was missing from phase 1, or it isn't actually needed.

Keep it short. A plan longer than the diff it describes is procrastination.

### HITL and AFK

- **HITL (human in the loop)** is the default when the slice changes user-visible behaviour, contracts, migrations, security, privacy, domain policy, architecture, dependencies, operational risk, or needs manual UI/product judgment. Name the decision or manual check and stop at that point for the owner.
- **AFK** is allowed only when behaviour is fully specified, no consequential choice remains, the change is low-risk and reversible, and deterministic checks can establish success. Discovering a new choice immediately reclassifies the slice to HITL.
- AFK means the implementation loop may run without synchronous user input. It never means auto-merge, skipped review, weaker evidence, or permission to expand scope.

If a slice only fits one context by omitting review or verification, it does not fit. Split it along another observable behaviour boundary.

## 2. Code

- Follow the project's existing conventions over your own preferences. Read neighbouring files first.
- Contract first: use types generated from or aligned with the canonical project contracts, then fill in the logic. If implementation needs a different boundary, revise the contract before coding against the new shape.
- No commented-out code, no TODOs without an issue reference, no silent `catch {}`.
- Handle every error path the contract promised. That is what the error table was for.

## 3. Test

Choose the primary evidence by mode:

- `full-feature-delivery`: run the selected phase-3 executable acceptance check before production code and confirm it fails for the expected missing behaviour.
- `bug-fix`: run the closest faithful regression test against the broken code, then against the fix.
- `compressed-small-change`: when behaviour changes, use a Gherkin-like native acceptance test unless project rules say otherwise. For mechanical configuration, documentation, refactor-only, or visual-only work, use the cheapest deterministic evidence that proves the requested outcome; do not manufacture a red behavioural test.

Choose the smallest set of test levels that can expose the changed risks and localize failures:

- **Unit** for deterministic domain rules, calculations, validation, state transitions, edge cases, and concurrency primitives that can be isolated without hiding the risk.
- **Component/service** for one deployable component through its real framework boundary with controlled external dependencies.
- **Integration** for database queries, migrations, brokers, caches, filesystem/network adapters, transactions, serialization, and framework wiring. Prefer real infrastructure or a faithful ephemeral substitute where mocks would hide the failure mode.
- **Contract** for changed or consumed HTTP/event/schema boundaries, compatibility, generated clients, and provider/consumer assumptions.
- **System/e2e** for critical cross-component journeys and risks that only appear in a running system. Do not require it for an isolated change when a closer boundary proves the behaviour.
- **Non-functional and specialist checks** for the affected risk: security, accessibility, performance, resilience, reconciliation/idempotency, migration safety, or visual evidence.

The phase-3 acceptance check may itself live at component, integration, contract, or system level. Do not duplicate it at every layer. Add a faster focused test first when it shortens the feedback loop for high-risk logic; no level is mandatory merely to demonstrate TDD or satisfy a pyramid. A confirmed bug still needs a regression test that fails on the broken version and passes on the fix, at the closest level that reproduces the defect faithfully.

Test the affected error paths, not only the happy path. The plan, evidence, and P4.4 ledger rows must say why each selected level applies. Unselected levels stay on the ledger as `N/A` with the changed-risk reason; they need no ceremonial test. A release-relevant risk with no evidence must be marked `Blocked` or explicitly accepted.

For full feature delivery, create `04-implementation/qa-metrics.md` from `assets/templates/qa-metrics.md` when the first slice starts. It is a compact evidence ledger, not a dashboard project: update the test-run, traceability, defect, and NFR rows as the relevant evidence exists. A bug fix or compressed change records the same applicable evidence in its issue unless the project already requires the shared QA document. Also update existing applicable rows in project-root `docs/quality/metrics.md`; create that rolling document only when the product stream genuinely needs it. Follow `references/qa-metrics.md`; an unavailable environment is **Blocked**, never silently counted as green.

Create project-root `docs/quality/gates.md` from `assets/templates/quality-gates.md` when the first feature needs deterministic quality gates. Run the applicable blocking gates for each slice and record the CI command/report. The gates cover contract compatibility, architecture boundaries, migration safety, Config/IaC policy, changed-code duplication and cognitive complexity, dependency/license policy, and performance regression on affected hot paths. Dead-code analysis is advisory: triage its candidates instead of blocking a release on guesses. See `references/quality-gates.md`.

Use mutation testing selectively where it adds signal: changed pure domain logic, authorization, money calculations, validation, state transitions, or critical transformations. Record the selected module, surviving non-equivalent mutants, and resulting action in `qa-metrics.md`. Do not run it indiscriminately across generated code, I/O wrappers, or the whole repository merely to produce a score.

For changed UI, data, or asynchronous flows, include the matching quality evidence in the project-level document: accessibility checks for critical user paths, reconciliation/idempotency checks for data and events, and flaky-test rate for the affected suite. These are conditional on the change; they must be recorded as `N/A` with a reason when they do not apply.

## 4. Review

Use a reviewer with a fresh context that did not produce the implementation whenever the harness supports it. Give it only durable inputs: the tracker item/slice, applicable approved artifacts, observable evidence, relevant project guidance, diff, and test results. Do not give it the implementer's persuasive narrative. If no independent reviewer is available, perform the review after a deliberate context break, mark P4.6 `Blocked` or `Done` as explicit self-review, and say so in that turn. Never complete a slice with the review row missing.

Review on two explicit axes:

1. **Specification fit** — the observable behaviour, FR/NFR, contracts, scenarios, error paths, and out-of-scope boundaries are satisfied without invented requirements.
2. **Engineering fit** — the change follows the project's architecture and conventions and is safe, operable, readable, and maintainable.

Use the strongest suitable reviewer available. A cheaper execution model is reasonable only for a fully specified, low-risk slice with deterministic tests; contracts, migrations, security, concurrency, architecture, and final review do not trade reasoning quality for token savings.

Report findings to the user rather than quietly fixing everything. This is also the **targeted defect review** — see `references/targeted-defect-review.md` for the confirmed-defect format:

- Does it actually satisfy the FR, or only the test?
- Contract drift: does the implementation still match phase 2?
- Security: authz on every entry point, input validation, no injection, secrets not in code, no PII in logs, tenant isolation enforced.
- Failure modes: timeouts, retries, idempotency, what happens when the dependency is down.
- Concurrency: races, transactions, isolation level.
- Observability: can you debug this in production from the logs and metrics it emits?
- Readability: would you understand this in six months?

Not every finding is the same kind of thing:

- **A confirmed defect** — the code does something wrong, not just something you'd have done differently — is a **bug**: record it per `references/targeted-defect-review.md` and either fix it with a regression test or explicitly accept it as an owned risk.
- **A deliberate tradeoff** — "acceptable for now, tenant isolation on this admin-only endpoint is enforced at the gateway, not here" — is a decision: record it as an owned risk in the selected tracker. Do not leave acceptance only in chat.

## 4a. Artifact handoff

When a session or implementation agent changes, update the current-slice handoff in `plan.md`. Transfer only durable, checkable state:

- ticket/slice ID, observable outcome, mode, and blocking dependencies;
- applicable approved artifacts, acceptance checks, and explicit owner decisions;
- current code/diff and exact test commands with results;
- remaining work, known failures, risks, and unresolved questions.

Do not use the chat transcript as the source of truth, and do not claim an unverified summary as completed work. The receiving agent reopens the referenced artifacts and reruns the cheapest relevant check before continuing. Create no separate handoff document unless work is actually moving between sessions or agents; the plan and iteration log remain the normal source of continuity.

## 5. Refactor

Only with tests green. Remove duplication, name things properly, split what grew too big, delete dead code. Behaviour must not change — if a test needs updating during refactoring, it was not a refactoring.

## 6. System / E2E when selected

When the changed risk requires a running system, run the selected smoke/acceptance journey against the appropriate ephemeral or integration environment, plus the applicable NFR checks. Do not start heavyweight infrastructure solely to tick an E2E box; report an unavailable required environment as Blocked.

## 7. Repeat or exit

**Any red step sends you back to plan.** Not back to code — back to plan, because a failure often means the slice was wrong, not just the line. Record it in `04-implementation/log.md`:

```
## 2026-07-12 — slice 3 (quote PDF)
Failed: e2e, PDF rendering timed out at 12 s (NFR-1 = 5 s).
Cause: Puppeteer cold start per request.
Change to plan: keep a warm browser pool; re-slice into 3a (pool) and 3b (render).
```

That log is the most valuable file in the folder when someone asks "why is it built like that?". Not every failed pass belongs in the tracker as a risk: a test that goes red and is fixed in the same loop is normal feedback. Only deliberately unresolved uncertainty earns a risk item.

## Exit criteria

For every mode:

- Every test level selected from changed risks is green; unavailable required checks are Blocked, never counted as passed.
- Every slice records an observable result, dependencies, and HITL/AFK mode; any decision discovered during AFK work was escalated before implementation continued.
- The plan or compact issue records changed risks, applicable test levels, and evidence without demanding unrelated levels.
- Each slice received fresh-context review on specification fit and engineering fit, or the lack of an independent reviewer is stated explicitly.
- Any session/agent handoff contains durable artifact links, exact test evidence, remaining work, and unresolved questions; the receiver reverified the starting state.
- Review findings are fixed with regression evidence or explicitly recorded as an owned accepted/mitigated risk; nothing remains only as verbal acceptance.
- Automatable NFRs are verified.
- The mode's evidence location distinguishes passed, failed, skipped, and blocked checks. Full features update `qa-metrics.md`; shorter modes use their compact tracker item unless the project profile requires more.
- Every applicable blocking quality gate passed. An exception has an explicit reason, owner, review date, and approved risk item; advisory findings are triaged rather than silently ignored.
- A changed schema has passed migration safety checks; a changed hot path has reproducible performance evidence against its baseline and NFR; a changed configuration or infrastructure manifest has passed its policy checks. Any unavailable gate is explicitly Blocked and decided as scope, risk, or work — never counted as green.
- For full feature delivery, every phase-3 acceptance check passes and one final targeted review covers the combined diff. That combined-diff pass is the phase-4 exit review in `references/phase-exit-review.md`; do not add another session after it.
- For a bug fix, the regression check is proven red on broken code and green on the fix.
- For a compressed change, the requested outcome has the declared observable evidence; a behavioural red/green cycle is required only when behaviour changed.
- Full-feature repo artifacts, or the shorter mode's compact issue, reflect the final state before release handoff.
- The P4 ledger is complete for every slice; unselected test levels, skipped e2e, and missing independent review are explicit rows, not absent.
