# QA metrics

Use these metrics to answer a useful question: *do we have evidence that this feature meets its promises, and did that evidence hold after release?* They are feature-scoped so a small change does not inherit an unrelated codebase's history or targets.

Start from `assets/templates/qa-metrics.md` in `docs/features/<slug>/04-implementation/qa-metrics.md`. Update it as evidence appears; do not wait until release and reconstruct a success story from memory.

For measures that need a rolling history, create one project-root document from `assets/templates/quality-metrics.md` at `docs/quality/metrics.md`. Keep one document per service or product stream; do not combine unrelated systems with different SLOs, deployment pipelines, or owners.

Keep deterministic CI rules separately in `docs/quality/gates.md`, created from `assets/templates/quality-gates.md`. A metric describes a trend; a gate answers whether a concrete change may proceed. See `references/quality-gates.md`.

## Rules for every metric

- State the scope: feature slug, commit or release identifier, environment, and observation window.
- Link the command, CI job, dashboard, test report, or issue that produced the number. A number without a source is a claim, not a metric.
- Report all four test outcomes: **passed**, **failed**, **skipped**, and **blocked**. A blocked Docker, database, browser, or credentials-dependent check is not a pass.
- Compare an NFR against its approved target and, when useful, the pre-change baseline. Do not invent a universal threshold in this file.
- Use `Not applicable` only with a reason. Use `Not yet observed` for a post-release metric whose window is still open.
- Keep counts by severity and source for defects. A low count can mean good quality or poor discovery; the source makes the number interpretable.

## Metrics to record

| Metric | How to calculate / record | Why it matters |
|---|---|---|
| Acceptance traceability | Release-scope FRs and contract error rows with at least one linked executable scenario / total release-scope FRs and error rows. List any manual or blocked verification separately. | Measures whether the agreed behaviour can actually be checked, rather than code volume or test count. |
| Test-run health | For each unit, integration, contract, and E2E suite: passed, failed, skipped, blocked, duration, and evidence link. Add a repeat-failure count when a suite is suspected flaky. | Makes a green result auditable and exposes missing runtime-dependent coverage. |
| Risk-based verification | For each release-scope NFR and named high-risk failure mode: target, test or probe, environment, sample/window, result, and evidence. | Connects quality work to the risks and NFRs the user approved, without substituting line coverage for evidence. |
| Mutation-test evidence (selected logic) | For a changed, high-risk, mostly deterministic module: killed, survived, and excluded-equivalent mutants; record why the module was selected and the follow-up for every non-equivalent survivor. | Checks whether tests detect small behaviour changes in logic where conventional coverage can look healthy while assertions are weak. |
| Confirmed defects and regression protection | Confirmed defects found before release, grouped by severity and source; escaped defects found during the release observation window; fixed confirmed defects with a linked regression test / fixed confirmed defects. | Shows discovery and escape separately, and preserves the rule that a fix is not done without a guardrail. |
| Release outcome | Release identifier, smoke-test result, observation window, whether rollback/hotfix/incident occurred, and time to detect/recover when applicable. | Tells whether the change held in the environment that matters, not merely in CI. |

## Project-level operational quality metrics

Keep the following measures in `docs/quality/metrics.md`. They need a time window and repeated observations, so a single feature snapshot only contributes evidence to them.

| Metric | Calculation / evidence | Use it well |
|---|---|---|
| SLO and error-budget burn | For each agreed availability, latency, or correctness SLO: SLI result, target, remaining error budget, and burn rate over a stated window. | An SLO without an error budget is only a dashboard number. Alert on a sustained burn that threatens the period, not every small fluctuation. |
| Test flake rate | Tests that fail initially and pass unchanged on retry / test executions, per suite and stated window. Keep failed tests that remain red separate. | A retry is diagnostic evidence, not permission to call a failed run green. Compare like-for-like suites and track the top flaky tests. |
| Security remediation age | Open findings by severity plus age, and time from discovery to verified remediation for closed high/critical findings. Link the scanner and issue. | Report exposure and response together; a clean scan today does not erase an old untriaged finding. |
| Data integrity | Reconciliation mismatches / records or events checked; duplicate-processing and idempotency failures; repair count. State the data source and window. | Use for writes, migrations, imports, and asynchronous workflows. Do not invent a zero target without considering expected corrections and measurement coverage. |
| Alert quality | Actionable alerts / alerts fired, false-positive count, median time to detect, and link to the response runbook. | An alert is actionable when it required investigation or action. Do not improve the ratio by suppressing alerts without an alternate detection path. |
| Accessibility | Automated violations by severity plus manually checked critical user paths, standard/tool version, and unresolved exceptions. | Apply when user-facing UI changes. Automated scans find only part of accessibility; critical journeys still need manual verification. |

## How to interpret it

These are decision aids, not targets to game:

- Do not set a repository-wide code-coverage percentage, raw test-count target, or "zero bugs" target. They reward superficial tests, splitting tests, or hiding defects.
- Mutation testing is a sharp tool, not a universal release gate. Use it for changed deterministic, high-risk logic where the language has a practical runner; exclude generated code, plumbing, and equivalent mutants with a reason. A surviving non-equivalent mutant is a prompt to strengthen a test or document an owned risk — never a reason to tweak production behaviour just to kill it. Do not impose one global mutation score.
- A failed, skipped, or blocked release-scope check needs a decision: fix it, reduce the release scope, or log an owned risk. It cannot disappear into an aggregate pass rate.
- A high defect count during review can be healthy when it means the process found problems before users did. Look for severity, source, trend across comparable releases, and escaped defects — never the count alone.
- Compare performance only when the workload, environment, and measurement window are stated. A local p95 is not production availability evidence.

## Phase use

- **Phase 3:** derive the traceability denominator from the approved FRs and contract error rows.
- **Phase 4:** update test-run health, risk-based verification, and confirmed-defect rows slice by slice. The phase-4 exit snapshot must be complete enough to make release risks visible.
- **Phase 5:** add smoke evidence and the release observation window. Only after it ends, record escaped defects and release outcome; an open window is `Not yet observed`, not success.
- **Project-level:** update the rolling document after applicable releases, scans, test runs, and incident reviews. `N/A` is better than a fabricated metric, but it needs a reason and an owner if the gap is material.

## DORA: a rolling delivery view, not a feature score

DORA is useful beside the feature snapshot because it shows whether the delivery system is improving over a stated service/team and rolling time window. Record each release event in the shared tracker, then calculate:

| Metric | Calculation | Reporting rule |
|---|---|---|
| Deployment frequency | Successful production deployments / stated window | Scope to one service or product stream; do not mix unrelated deployment pipelines. |
| Lead time for changes | Merge or commit timestamp → production deployment timestamp | Report median and p95 with sample count; state the chosen start event consistently. |
| Change failure rate | Deployments needing rollback, hotfix, or incident response / production deployments | Count the outcome once per deployment and keep the classification evidence. |
| Time to restore service | Detection timestamp → service restored timestamp for qualifying incidents | Report median with incident count; no incidents is `N/A (no events)`, not zero minutes. |

Do not make any DORA number a per-feature release gate or rank people with it. A single deployment does not establish a trend. The feature snapshot supplies the release identifier, timestamp, and outcome; the shared tracker supplies the comparable history.
