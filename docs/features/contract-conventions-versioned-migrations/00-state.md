# Workflow state: contract-conventions-versioned-migrations

External tracking: [Linear project](https://linear.app/mischa8925/project/contract-conventions-and-versioned-migrations-43e18a862167)

Detailed workflow state is kept here because the Linear integration did not authorize publishing private repository architecture in external documents.

## Current phase

- Phase: 2 — Contracts
- Status: ready to draft contracts
- Phase 1 approval: approved by Michael on 2026-08-09 ([MIS-5](https://linear.app/mischa8925/issue/MIS-5/gate-approve-contract-conventions-and-versioned-migrations-concept))
- Phase 2 approval: not started

## Risks

| ID | Status | Risk | Likelihood | Impact | Mitigation / decision | Owner |
|---|---|---|---|---|---|---|
| RISK-1 | Open | Existing deployed schemas may differ from the committed version-1 SQL because some services have used Hibernate schema updates. Baseline could otherwise record an invalid schema as current. | Medium | High | Never enable automatic baseline-on-migrate. Require a backup and schema comparison before the explicit version-1 baseline of any non-empty database. | Michael |

## Open questions

None. The concept assumes deployed data must be preserved and that live production migration is a separate, explicitly approved release action.

## Decisions

| Date | Decision | Reason |
|---|---|---|
| 2026-08-09 | Use one central migration runner for all six application databases. | It gives Java and Go owned databases one ordered, checksum-tracked path without adding migration libraries to every service. |
| 2026-08-09 | Keep database/role/extension provisioning separate from application schema migrations. | PostgreSQL initialization is one-time provisioning; versioned schema evolution must run on existing volumes too. |
| 2026-08-09 | Keep automatic baseline of non-empty databases disabled. | It could hide schema drift and mark a wrong schema as valid. |
| 2026-08-09 | Michael approved the bilingual Phase 1 concept. | The workflow can enter Phase 2; contracts and implementation remain gated. |
