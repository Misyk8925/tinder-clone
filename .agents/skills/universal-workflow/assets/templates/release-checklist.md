# Release checklist: <feature-slug> — <version>

## Risk register (selected tracker)
- [ ] Selected tracker's Open-risk view is empty — every risk is Mitigated, Accepted with an owner/review date, or Closed
- [ ] If one is still Open, this release isn't ready — not even with a note promising to fix it later
- [ ] Repo fallback: same check against `00-state.md`

## Build
- [ ] Artifact tagged with git sha
- [ ] Base image pinned by digest, non-root user
- [ ] Lint + all risk-selected applicable suites green in CI; unavailable required checks are Blocked
- [ ] Migrations packaged and runnable separately

## Security scan
- [ ] Dependencies (SCA) — no high/critical
- [ ] Container image (Trivy/Grype) — no high/critical
- [ ] SAST — no high/critical
- [ ] Secret scan clean
- [ ] Config hardened (no debug, TLS, CORS not `*`)
- [ ] Any accepted finding is an owned risk in the selected tracker, not only noted here

## Deploy
- [ ] Migrations backward-compatible with running version
- [ ] Changed migrations pass on an empty schema and preserve compatibility with the current application version
- [ ] Rollout strategy: <blue-green | canary | rolling>
- [ ] Rollback: <how>, trigger: <what>, who may pull it without asking: <name>
- [ ] Rollback rehearsed (not just documented)
- [ ] Migrations reversible OR expand/contract so old code runs on new schema
- [ ] Feature flag: <name | n/a>

## Smoke tests
- [ ] Health / readiness OK
- [ ] Smoke-tagged/grouped acceptance checks pass against the deployed environment
- [ ] One real end-to-end path with a test tenant

## QA metrics
- [ ] `04-implementation/qa-metrics.md` links the test, traceability, defect, and NFR evidence for this release
- [ ] Each NFR measurement names its target, environment, sample/window, and result (or an explicit Blocked / Not applicable reason)
- [ ] Selected high-risk changed logic has mutation-test evidence, or a recorded `N/A` reason
- [ ] Release identifier and smoke-test result recorded
- [ ] Post-release observation window: <start — end>; outcome is `not yet observed` until it ends
- [ ] Release timestamp, source revision, and outcome available to the rolling DORA tracker
- [ ] Applicable rows in `docs/quality/metrics.md` updated, or marked `N/A` with a reason
- [ ] Applicable blocking checks in `docs/quality/gates.md` passed for this release
- [ ] Changed Config/IaC passes its schema and security policy checks (TLS, CORS, non-root, no debug/default credentials)
- [ ] Changed hot paths meet their agreed NFR against a reproducible baseline, or are `N/A` with a reason
- [ ] Any duplication/complexity exception has approval, owner, review-by date, and a linked `risk` issue
- [ ] Dead-code candidates are triaged; no finding is silently ignored

## Monitoring
- [ ] RED metrics per endpoint
- [ ] Domain metric: <...>
- [ ] Alerts wired to NFR-1..N
- [ ] Structured logs with trace id, no PII
- [ ] Dashboard: <link>
- [ ] Error tracking tagged with release version

## Docs
- [ ] API docs / OpenAPI published
- [ ] CHANGELOG entry
- [ ] Runbook: purpose, dependencies, top-3 failure modes
- [ ] Concept doc reconciled with what was actually built
- [ ] Full feature: all phase milestones complete in the selected tracker; shorter mode: compact item closed with evidence

## If it fails
- [ ] Rollback verified with the same smoke tests
- [ ] Affected users informed
- [ ] Incident written to `05-release/incidents/<date>-<slug>.md`
- [ ] Executable regression acceptance check added and shown failing on the broken version
- [ ] Post-mortem actions merged (an incident is closed when they are, not when the service is back)
