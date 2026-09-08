# ECS solution proofs

Repo-local tracker: [`00-state.md`](00-state.md). Linear is not authorized in this environment.

Indexes canonical artifacts. Does not copy specs or tests.

This feature is in **phase 1 discovery**. The concept brief is not drafted yet: owner choices on the frontier still sit at `Proposed`.

| Folder | What to open |
|---|---|
| [`00-state.md`](00-state.md) | Tracker, decisions, risks, phase ledger |
| [`inventory.md`](inventory.md) | Wayfinder W1: comparable solution pairs found in the repo |
| `01-concept.ru.md` | Not written — waiting for shared-understanding confirmation |

## Boundary that must not drift

Production Dokploy Compose (`docker-compose.yml` + host certs under `/etc/dokploy/certs/tinderclone/`) is not the experiment runtime. Proofs run in an isolated AWS sandbox and must not share Kafka consumer groups, Redis keyspaces, databases, or TLS material with production.

No experiment may require production `.env`, AWS keys, or live user traffic unless the owner later approves that in writing.

## Phase ledger — routing + phase 1

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `full-feature-delivery`: new experiment runtime, cross-service variants, cost/risk owner choices |
| R.2 Project profile | Done | repo-local tracker; Russian concept (user request); English contracts only if a public API appears later; native tests; no AWS deploy from this Cloud agent |
| P1.1 Tracker container | Done | `00-state.md` (Linear MCP `needsAuth`) |
| P1.2 Feature index | Done | this file |
| P1.3 Decision tree | Done | `00-state.md` § Decision tree |
| P1.4 Frontier interview | Done this round | four owner questions in `00-state.md`; waiting for answers |
| P1.5 Category check | Done | inferred vs asked in `00-state.md` |
| P1.6 Wayfinder split | Done | W1 inventory complete; W2 measurement and W3 spend envelope wait on owner |
| P1.7 Shared-understanding confirmation | Blocked | waiting owner correction of the summary |
| P1.8 Concept FR/NFR | Blocked | not drafted until P1.7 |
| P1.9 Suggested solution | Blocked | not drafted until P1.7 |
| P1.10 Diagram | Blocked | depends on approved topology |
| P1.11 Self-check | N/A | no concept draft yet |
| P1.12 Phase-1 gate | Blocked | not requested until concept exists |
