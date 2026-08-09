# Deterministic quality gates: <codebase-or-service>

**Owner:** <team / role>  
**Baseline:** <main branch / latest release tag / consumer contract version>  
**CI evidence index:** <workflow or report links>  
**Last reviewed:** <ISO-8601 timestamp>

Use blocking checks for deterministic regressions. A `Blocked` check is not a pass. Tool choice and thresholds belong here so they are stable, inspectable, and changed deliberately.

## Blocking gates

| Gate | Tool and command | Scope / baseline | Threshold or policy | Latest result | CI report |
|---|---|---|---|---|---|
| API / AsyncAPI compatibility | | | No unapproved breaking change | Passed / Failed / Blocked / N/A | |
| Architecture boundaries | | | <forbidden dependency rules> | Passed / Failed / Blocked / N/A | |
| Migration safety | | Empty schema + current-app compatibility | Expand/contract for destructive changes | Passed / Failed / Blocked / N/A | |
| Config / IaC policy | | Changed config/manifests | Schema valid; TLS/CORS/non-root/no debug/default credentials | Passed / Failed / Blocked / N/A | |
| Changed-code duplication | | Target-branch diff | <introduced duplication policy> | Passed / Failed / Blocked / N/A | |
| Changed-code cognitive complexity | | Target-branch diff | <introduced complexity policy> | Passed / Failed / Blocked / N/A | |
| Dependency vulnerability policy | | Resolved dependency graph | <severity / remediation policy> | Passed / Failed / Blocked / N/A | |
| Dependency licence policy | | Added/resolved dependencies | <allowed / prohibited licences> | Passed / Failed / Blocked / N/A | |
| Performance regression | | Affected hot path; baseline revision | <NFR and allowed regression tolerance> | Passed / Failed / Blocked / N/A | |

### Migration evidence — when schema changes

| Migration / schema version | Empty-schema result | Current-app compatibility result | Expand/contract plan | Evidence / blocker |
|---|---|---|---|---|
| | Passed / Failed / Blocked | Passed / Failed / Blocked | <link / N/A> | |

### Performance evidence — when a hot path changes

| Path / operation | NFR | Baseline revision + result | Current result | Workload and environment | Evidence / blocker |
|---|---|---|---|---|---|
| | | | | <data, concurrency, duration> | |

## Advisory: dead-code analysis

| Tool / command | Changed scope | Candidate | Confidence | Decision | Reason / issue / evidence |
|---|---|---|---|---|---|
| | | | | Removed / Kept / Investigate | |

## Approved changed-code exceptions

| Gate | File / module | Reason and risk | Remediation | Owner | Review-by / expiry | Approval and risk issue |
|---|---|---|---|---|---|---|
| Duplication / complexity only | | | | | | |

## History

| Date | Change to rule, tool, threshold, or policy | Decision / owner | Evidence |
|---|---|---|---|
| | | | |
