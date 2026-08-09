# QA metrics: <feature-slug> — <commit-or-release-id>

**Scope:** <release-scope / exclusions>  
**Environment:** <local / CI / staging / production>  
**Reporting time:** <ISO-8601 timestamp>  
**Evidence index:** <CI run / dashboard / test report links>

## 1. Acceptance traceability

| Requirement / contract error | Scenario or manual check | Status | Evidence / reason |
|---|---|---|---|
| FR-1 | | Passed / Failed / Skipped / Blocked / N/A | |

**Traceability:** <verified release-scope rows> / <all release-scope rows>

## 2. Test-run health

| Suite | Passed | Failed | Skipped | Blocked | Duration | Evidence / blocker |
|---|---:|---:|---:|---:|---:|---|
| Unit | | | | | | |
| Integration | | | | | | |
| Contract | | | | | | |
| E2E | | | | | | |

**Repeat failures / suspected flakes:** <none, or suite + count + issue link>

### Mutation testing — selected high-risk logic only

| Module / logic | Why selected | Killed | Survived | Equivalent excluded | Score | Action / evidence |
|---|---|---:|---:|---:|---:|---|
| | | | | | | |

`N/A` is valid only with a reason (for example: no changed deterministic high-risk logic, or no practical runner).

## 3. Risk-based verification

| NFR / risk | Approved target | Test or probe | Environment + sample/window | Result | Evidence / blocker |
|---|---|---|---|---|---|
| NFR-1 | | | | Passed / Failed / Blocked / N/A | |

## 4. Confirmed defects and regression protection

| Source | Blocker | Major | Minor | Cosmetic | Evidence / issue links |
|---|---:|---:|---:|---:|---|
| Before release (tests/review/audit) | | | | | |
| Escaped in observation window | | | | | |

**Fixed confirmed defects with linked regression test:** <count> / <count>

## 5. Release outcome

| Field | Value | Evidence |
|---|---|---|
| Release identifier | | |
| Smoke tests | Passed / Failed / Blocked | |
| Observation window | <start — end, timezone> | |
| Outcome | Not yet observed / Clean / Rollback / Hotfix / Incident | |
| Time to detect / recover | N/A or <duration> | |

### DORA event data (feeds a shared rolling tracker)

| Field | Value | Evidence |
|---|---|---|
| Production deployment timestamp | | |
| Change start timestamp (commit or merge; state which) | | |
| Outcome classification | Clean / Rollback / Hotfix / Incident / Not yet observed | |
| Incident detection / restore timestamps | N/A or <timestamps> | |

## Decision

<Ready to release / scope reduced / blocked / risk accepted — owner and issue link>
