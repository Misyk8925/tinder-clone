# Deterministic quality gates

Use deterministic gates to make a specific class of regression impossible to wave away in review. They are CI evidence for a release, not a leaderboard and not a substitute for feature tests. Create `docs/quality/gates.md` from `assets/templates/quality-gates.md` once per codebase and set tools/commands that fit the actual stack.

## Blocking gates

| Gate | What it checks | Scope and decision rule |
|---|---|---|
| API / AsyncAPI compatibility | A proposed API or event contract against the latest released or consumer-supported baseline. | Block accidental breaking changes. An intentional incompatible change belongs in phase 2, with consumer migration/versioning and explicit approval; it is not suppressed in CI. |
| Architecture boundaries | Forbidden dependencies and allowed layer/module directions. | Block a new dependency that violates the agreed boundary. Keep the rules few and concrete: a rule no developer can explain is not a useful gate. |
| Migration safety | A changed migration on an empty schema and against a schema used by the currently deployed application version. | Block a migration that cannot initialize a clean database or breaks old-code compatibility. Destructive changes need an expand/contract plan: add first, migrate/backfill, switch readers/writers, remove only in a later release. |
| Config / IaC policy | Application configuration and changed infrastructure manifests against their schemas and security policies. | Block invalid configuration, plaintext/default credentials, debug mode, missing TLS, wildcard CORS without an explicit boundary decision, or a root container. Run Terraform/Kubernetes policy checks when those manifests change. |
| Changed-code duplication and cognitive complexity | New or edited production code compared with the target branch baseline. | Block only introduced duplication or newly excessive complexity, not legacy debt unrelated to the diff. Record tool/version and threshold in the gates document. |
| Dependency and licence policy | Added or resolved dependencies against vulnerability, provenance, and licence policy. | Block prohibited licences and unresolved high/critical exposure according to the approved policy. A vulnerability that cannot be fixed is an explicit, time-bounded accepted risk — never a blanket ignore. |
| Performance regression | A changed hot path against its reproducible baseline and approved NFR under the same workload profile. | Block a regression that violates the NFR or agreed tolerance. Record environment, data shape, concurrency, duration, and the baseline revision; local measurements are not production availability evidence. |

## Advisory gate

**Dead-code analysis** is advisory because static tools cannot reliably prove that reflection, configuration, generated bindings, frameworks, or external callers do not use code. Report candidates with tool confidence and changed scope; then record `removed`, `kept — reason`, or `needs investigation`. Do not make a release red solely because a candidate was found.

## Exceptions

An exception is a visible engineering decision, not an annotation that disables a rule:

- Limit it to a named file/module and changed code.
- State why the change cannot meet the threshold now, what risk it creates, and the smallest remediation plan.
- Assign an owner and review-by/expiry date.
- Link a Linear `risk` issue (or the fallback risk register) and record the user's explicit approval when the exception changes the release risk.
- Re-run the gate at the review date. Expired exceptions are failures until renewed deliberately.

Do not use exceptions for contract compatibility, migration safety, secret scanning, Config/IaC policy, or prohibited licences. Those need a contract migration, expand/contract plan, remediation, or an explicit release decision outside the gate — not a hidden suppression. A performance failure means the NFR, scope, or implementation needs a decision; do not alter the baseline or workload merely to make a comparison pass.

## Evidence

For every gate report the command or CI job, baseline revision, changed scope, outcome, and report link. A tool that could not run is `Blocked`, not `Passed`; decide whether to unblock it, reduce the release scope, or log an owned risk.
