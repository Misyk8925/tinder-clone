# Phase 5 — Release

Goal: get it into production, know within minutes whether it works, and be able to go back.

Each step gates the next. A failing scan does not "get fixed after deploy".

Scale this phase by router mode:

- `full-feature-delivery` uses the complete release path below.
- `bug-fix` runs the build, security, migration, smoke, rollback, and monitoring checks affected by the defect and fix. A hotfix does not waive a relevant check.
- `compressed-small-change` runs the affected build/validation checks and records a rollback note when runtime behaviour changes. Do not manufacture deployment, monitoring, milestone, or documentation work for an unreleased local change.

Deploy only when the user's request and project process authorize deployment. Otherwise hand off verified release evidence and state what remains unexecuted. Keep every P5 ledger row from `references/phase-ledger.md`; unused full-release steps are `N/A` or `Mode-omit` with the mode reason, never dropped from the table.

## 1. Build

- Reproducible, versioned artifact (image tag = git sha, not `latest`).
- Multi-stage build, non-root user, pinned base image digest.
- Migrations bundled and runnable separately from the app start.
- Build fails on lint errors and failing tests — not warnings that everyone ignores.

## 2. Security scan

Run before deploy, fail the pipeline on high/critical:

- Dependencies (SCA): `npm audit` / `osv-scanner` / Snyk / Dependabot.
- Container image: Trivy or Grype.
- Static analysis (SAST): Semgrep, CodeQL, SonarQube.
- Secrets: gitleaks / trufflehog on the repo and the image.
- Config: no debug mode, no default credentials, TLS enforced, CORS not `*`.

Anything accepted instead of fixed becomes an owned risk in the selected tracker, not a checklist footnote.

## 3. Deploy

- Migrations first, and they must be backward-compatible with the currently running version — expand/contract, never a destructive change in the same release as the code that needs it.
- Progressive rollout where the platform allows it (blue/green, canary). Otherwise: deploy at a time when you can watch it.
- Rollback is decided *before* deploying, not during the incident: how, and what the trigger is.
- Feature flag for anything user-visible and risky.

## 4. Smoke tests

Run against production (or the freshly deployed environment) immediately:

- Health/readiness endpoints.
- The smoke-tagged or smoke-grouped executable acceptance checks from phase 3 — you already wrote them.
- One real end-to-end path with a test tenant.

Failing smoke tests trigger the rollback you planned in step 3. Automatically, if the pipeline can.

## 5. Monitoring

Before you call it done, make sure you would *find out* if it broke:

- Metrics: RED (rate, errors, duration) per endpoint, plus the domain metric that actually matters (quotes generated per day).
- Alerts tied to the NFRs from phase 1 — an NFR with no alert is a hope. Alert on symptoms (error rate, latency), not on causes (CPU).
- Logs: structured, correlation/trace id, no PII.
- Dashboard showing the new feature, linked from the release checklist.
- Error tracking (Sentry or equivalent) with the release version tagged, so a spike points at a deploy.
- Update `04-implementation/qa-metrics.md` with the release identifier, smoke-test evidence, and a stated observation window. At its end, record the observed error/latency/domain results, whether rollback, a hotfix, or an incident was needed, and time to detect/recover when applicable. Do not write “zero incidents” before the window has elapsed — write `Not yet observed` instead.
- Send the release timestamp, source revision, and outcome to the service/team's rolling DORA tracker. DORA needs comparable releases over time; do not calculate or gate it from one feature snapshot.
- Update `docs/quality/metrics.md` with the release's SLO/error-budget, test-flake, security, data-integrity, alert-quality, and accessibility evidence where applicable. It is a rolling operational document, so state the window and sample count rather than treating one release as a trend.
- Confirm that the applicable blocking checks in `docs/quality/gates.md` passed against this release. An accepted exception must link its explicit approval and `risk` issue; dead-code candidates remain advisory but must be triaged with a decision.
- For a release with schema, configuration/infrastructure, or hot-path changes, link the migration-safety, Config/IaC-policy, and performance-regression reports in that gate document. A failed report returns to the phase that owns the defect; it is not bypassed with a local ignore.

## 6. Docs

- README / API docs updated (Swagger, OpenAPI published).
- CHANGELOG entry.
- Runbook: what this thing does, its dependencies, the top three failure modes and what to do about each.
- For `full-feature-delivery`, all phase milestones are complete and the concept is amended if reality diverged. For shorter modes, close the compact tracker item with scoped evidence.

## Definition of done

Deployed, smoke-tested, observable, documented, rollback rehearsed, and the user has seen it work. Use `assets/templates/release-checklist.md`.

The QA metrics snapshot is complete for the release: pre-release evidence is linked, NFR measurements name their environment and window, and post-release outcomes are either observed or explicitly still pending. See `references/qa-metrics.md`.

**The selected tracker's Open-risk view is empty.** This does not mean zero risk. Every risk is Mitigated, Accepted with an owner, or Closed; an undecided risk does not ship.

**No open blocker-severity bug in this delivery scope.** Bugs found through targeted review gate the release like risks do — see `references/targeted-defect-review.md`.

Show the completed P5 ledger with the release handoff. A step that was not run is `N/A`, `Blocked`, `Deferred`, or `Mode-omit` on that table. That handoff is the phase-5 exit review; do not add a second “please approve the release process” stop unless a deploy decision still needs the owner.

---

# When the release fails

A failed release is a normal event, not an emergency — *if* you decided in advance where each failure sends you. The rule that keeps the process honest: **you never fix forward under pressure into a system you cannot observe.** Stabilise first, diagnose second, fix third.

## Where each failure sends you

| Failing step | Blast radius | Go back to |
|---|---|---|
| Build | none — nothing shipped | Phase 4, plan. It is a code or CI problem. |
| Security scan | none | Phase 4 if it is your code; phase 2 if the fix changes the contract (e.g. an auth model turned out to be wrong); accept-with-reason only if the user signs it. |
| Deploy (never came up healthy) | none to users, if the rollout was progressive | Roll back, then phase 4. Check config, secrets, migrations first — that is where most of these live. |
| Migration | **high** — data | Stop. Do not retry blindly. See below. |
| Smoke tests | small, if caught in minutes | Roll back immediately, then phase 4. |
| Monitoring shows degradation after rollout | growing with time | Roll back or flip the feature flag, then run the incident loop. |
| A phase-1 NFR is violated in production | design-level | Roll back, then **phase 1** — the design, not the code, was wrong. |

Note the last row. If p95 is 4× the NFR and no amount of tuning closes the gap, the answer is not another commit. It is that the concept picked the wrong architecture, and the concept doc has to be reopened. That is what the "rejected alternatives" section is for — one of them may now be the right one.

## Rollback

Rollback is the default reaction, not the last resort. It costs a deploy; debugging in production costs users.

1. **Trigger it fast.** Whoever is watching the deploy can roll back without asking permission. Agree that before the release, not during it.
2. **Order:** feature flag off (seconds) → previous image (minutes) → migration reversal (hours, and only if unavoidable).
3. **Migrations are the reason this is hard.** If you followed expand/contract, the old code still runs against the new schema, and a code-only rollback is safe. If you did not, you now have a data problem instead of a deploy problem — this is why the phase-5 rule exists.
4. **Verify the rollback.** Run the same smoke tests against the restored version. A rollback that nobody verified is a second untested deploy.
5. **Announce it** to whoever is affected before they discover it themselves.

## Fix forward vs roll back

Fix forward only when *all* of these hold: the cause is understood, the fix is small and reviewed, tests cover it, and the system is not currently harming data or users. Otherwise roll back. "It is a one-line fix" is the sentence that precedes most bad nights.

## The incident loop

When users are already affected:

```
stabilise → communicate → diagnose → fix → verify → post-mortem
```

- **Stabilise** — restore service (rollback, flag, scale, rate-limit, degrade gracefully). Use the project's live incident channel or selected tracker; the durable repo record comes after stability.
- **Communicate** — one person keeps affected users and the team updated at a fixed interval, even when the update is "still working on it". Comments on the `incident` issue are the natural place for this.
- **Diagnose** — logs, traces, metrics, the diff that shipped. The release version tag on your error tracker is what makes this a five-minute job instead of a two-hour one.
- **Fix** — through the normal phase-4 loop. An incident does not license skipping tests. If a test or release check cannot run, put it on the ledger as `Blocked`; do not omit it.
- **Verify** — smoke tests plus the specific scenario that failed.
- **Post-mortem** — see below.

Once stable, write the durable technical record to `05-release/incidents/<date>-<slug>.md` from `assets/templates/incident.md`, and link it from the `incident` issue before closing that issue out.

## Post-mortem, and closing the loop

Blameless — you are looking for the missing guardrail, not the guilty commit. The output is not a document, it is **changes to the earlier phases**:

- **An executable regression check.** Follow the project convention; when none exists, use a clearly named Gherkin-like Given/When/Then test in the native framework. It fails against the broken version and passes against the fix.
- **A new or corrected NFR in phase 1**, if the failure was a limit nobody had written down (a queue depth, a timeout, a payload size).
- **A contract change in phase 2**, if a client's assumption was wrong or an error case was missing from the table.
- **A new alert**, if you found out from a user instead of from monitoring. That is a monitoring bug, and it is more urgent than the original bug.
- **A checklist line in phase 5**, if a step would have caught it and was not there.

Then update the selected tracker: what failed, what changed, and which phase the work returned to. Any action that cannot be completed now becomes an owned risk with a review date instead of living only in the incident file.
