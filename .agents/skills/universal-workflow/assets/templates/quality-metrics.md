# Operational quality metrics: <service-or-product-stream>

**Owner:** <team / role>  
**Scope:** <service, product stream, or bounded UI>  
**Reporting window:** <start — end, timezone>  
**Last updated:** <ISO-8601 timestamp>  
**Evidence index:** <dashboards, CI runs, scanner reports, incidents, runbooks>

Use one document for one comparable delivery stream. All numbers need a window, sample count where relevant, and an evidence link. `N/A` requires a reason; `Not yet observed` means the window is still open.

## 1. SLO and error budget

| SLO / SLI | Target | Window | Current result | Remaining budget | Burn rate | Alert / evidence |
|---|---|---|---|---|---|---|
| API availability | | | | | | |
| API p95 latency | | | | | | |
| <correctness SLI> | | | | | | |

## 2. Delivery (DORA)

| Metric | Value | Sample count | Window | Evidence / notes |
|---|---:|---:|---|---|
| Deployment frequency | | | | |
| Lead time for changes — median / p95 | | | | |
| Change failure rate | | | | |
| Time to restore service — median | | | | |

**Definitions:** change start = <commit / merge>; failure = <rollback / hotfix / incident>; qualifying incident = <definition>.

## 3. Test reliability

| Suite | Executions | Initial failures | Retry passes (flakes) | Flake rate | Still failing | Top flaky tests / evidence |
|---|---:|---:|---:|---:|---:|---|
| Unit | | | | | | |
| Integration | | | | | | |
| Contract | | | | | | |
| E2E | | | | | | |

## 4. Security remediation

| Severity | Open findings | Oldest open age | Closed in window | Median time to verified remediation | Evidence / owner |
|---|---:|---:|---:|---:|---|
| Critical | | | | | |
| High | | | | | |
| Medium | | | | | |

## 5. Data integrity — if the scope writes data or events

| Check | Records/events checked | Mismatches | Duplicate/idempotency failures | Repairs | Window | Evidence / owner |
|---|---:|---:|---:|---:|---|---|
| <reconciliation or invariant> | | | | | | |

`N/A`: <reason, if this scope does not write data or events>

## 6. Alert quality

| Alert / SLO | Alerts fired | Actionable | False positives | Median time to detect | Runbook / evidence |
|---|---:|---:|---:|---:|---|
| | | | | | |

## 7. Accessibility — if the scope changes user-facing UI

| Check / critical path | Tool or method | Critical | Serious | Other | Manual result | Exceptions / evidence |
|---|---|---:|---:|---:|---|---|
| | | | | | | |

`N/A`: <reason, if there is no user-facing UI change>

## Decisions and follow-up

| Date | Finding / trend | Decision or action | Owner | Issue / evidence |
|---|---|---|---|---|
| | | | | |
