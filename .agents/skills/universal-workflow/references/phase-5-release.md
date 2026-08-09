# Phase 5 — Release

Goal: get it into production, know within minutes whether it works, and be able to go back.

Each step gates the next. A failing scan does not "get fixed after deploy".

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

Anything you accept instead of fixing becomes a Linear issue labeled `risk`/`risk-accepted`, with a reason and an owner — not just noted in the checklist and forgotten once the release ships.

## 3. Deploy

- Migrations first, and they must be backward-compatible with the currently running version — expand/contract, never a destructive change in the same release as the code that needs it.
- Progressive rollout where the platform allows it (blue/green, canary). Otherwise: deploy at a time when you can watch it.
- Rollback is decided *before* deploying, not during the incident: how, and what the trigger is.
- Feature flag for anything user-visible and risky.

## 4. Smoke tests

Run against production (or the freshly deployed environment) immediately:

- Health/readiness endpoints.
- The `@smoke`-tagged scenarios from phase 3 — you already wrote them.
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
- All 5 Linear milestones complete; concept Documents amended if reality diverged from them — a concept doc that lies is worse than none. (No Linear: `00-state.md` closed out, `01-concept.*.md` updated.)

## Definition of done

Deployed, smoke-tested, observable, documented, rollback rehearsed, and the user has seen it work. Use `assets/templates/release-checklist.md`.

The QA metrics snapshot is complete for the release: pre-release evidence is linked, NFR measurements name their environment and window, and post-release outcomes are either observed or explicitly still pending. See `references/qa-metrics.md`.

**The Linear project's `risk-open` view is empty.** Not zero risk — that's not achievable and claiming it is worse than not claiming it. Every risk raised across all four earlier phases is, by now, Mitigated, Accepted with an owner, or Closed. Shipping with one `risk-open` issue left isn't a smaller version of done; it's a risk nobody actually decided about, which is exactly the outcome the register exists to prevent.

**No open `bug` issue with severity `blocker` in this feature's affected scope.** Bugs found via targeted review during phase 4, or via a full audit that touched this feature's scope, gate the same way risks do — see `references/bug-hunt.md`. A blocker-severity bug found elsewhere in the codebase (a full audit finding outside this feature's scope) doesn't automatically block this release, but flag it to the user if the scopes overlap.

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

- **Stabilise** — restore service (rollback, flag, scale, rate-limit, degrade gracefully). Do not hunt the root cause while it is bleeding. In parallel, open a Linear issue labeled `incident` — it's what people watch and get notified on while it's live; the repo file comes after. (No Linear: whatever the team already uses for live incident comms — Slack thread, PagerDuty, an issue in the fallback tracker — the point is a place people are already watching, not a new one; skip straight to the repo file if there's genuinely nothing.)
- **Communicate** — one person keeps affected users and the team updated at a fixed interval, even when the update is "still working on it". Comments on the `incident` issue are the natural place for this.
- **Diagnose** — logs, traces, metrics, the diff that shipped. The release version tag on your error tracker is what makes this a five-minute job instead of a two-hour one.
- **Fix** — through the normal phase-4 loop. An incident does not license skipping tests.
- **Verify** — smoke tests plus the specific scenario that failed.
- **Post-mortem** — see below.

Once stable, write the durable technical record to `05-release/incidents/<date>-<slug>.md` from `assets/templates/incident.md`, and link it from the `incident` issue before closing that issue out.

## Post-mortem, and closing the loop

Blameless — you are looking for the missing guardrail, not the guilty commit. The output is not a document, it is **changes to the earlier phases**:

- **A regression scenario in phase 3.** Every production failure becomes a Gherkin scenario, tagged `@regression`, that fails against the broken version and passes against the fix. This is the single most valuable thing you take out of an incident: the bug can now only happen once.
- **A new or corrected NFR in phase 1**, if the failure was a limit nobody had written down (a queue depth, a timeout, a payload size).
- **A contract change in phase 2**, if a client's assumption was wrong or an error case was missing from the table.
- **A new alert**, if you found out from a user instead of from monitoring. That is a monitoring bug, and it is more urgent than the original bug.
- **A checklist line in phase 5**, if a step would have caught it and was not there.

Then post a Linear Project Update on the feature: what failed, what changed, and which phase the work went back to. Any action from the table above that can't be merged right now — not "eventually", a real gap in when it'll happen — becomes a Linear issue labeled `risk`/`risk-accepted` (with an owner and a review-by date) instead of living only in the incident file where it's easy to forget. A feature that survived an incident and came back with tests around the wound is in better shape than one that never broke.
