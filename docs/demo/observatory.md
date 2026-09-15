# Matching observatory (local)

A stack-free city of agents that ranks the same way `services/deck` does today
(`AgeCompatibilityStrategy` + `LocationProximityStrategy`) and compares it to a
`log(1+likes)` popularity arm with a 12% newcomer quota.

No AWS, no Kafka, no Compose. Open `clients/tinder-admin` and press Play.

## Compact delivery record

| Field | Value |
|---|---|
| Outcome | Operator SPA shows a live city: likes, matches, and matches/100 swipes for `baseline` vs `popularity` |
| Out of scope | AWS stand, Keycloak, seeding `profile.created`, embeddings ranker, changing deck production scoring, ADMIN vs USER_ADMIN |
| Affected contract | none |
| Observable evidence | Vitest acceptance in `clients/tinder-admin/src/sim/*.spec.ts`; `npm run build`; browser Play → two arms and rising match pulses |
| Validation | `npm test` and `npm run build` in `clients/tinder-admin` |
| Rollback | Revert the commit. No service runtime behaviour changes |
| Promotion | One slice. In-browser sim is not a live-stack experiment |

## How to run

```bash
cd clients/tinder-admin
npm ci
npm test
npm run dev
```

Open the printed local URL. ## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: one local UI + in-browser sim, no AWS |
| R.2 Project profile | Done | Repo-local compact record; English to match demo kit; Vitest like the Angular client |
| R.3 Omitted ceremony | Done | P1.1–P1.12, P2.*, P3.*, P4.10 Mode-omit |
| C.1 Outcome / out of scope | Done | Table above |
| C.2 Affected contract | Done | none |
| C.3 Acceptance | Done | Gherkin-like Vitest in `src/sim/engine.spec.ts` |
| C.4 Test levels and commands | Done | `npm test`; `npm run build` in `clients/tinder-admin`. Integration/e2e N/A — no service boundary |
| C.5 Rollback | Done | Git revert; production scoring unchanged |
| C.6 Promotion check | Done | One slice; live-stack experiment still out of scope |
| P1–P3 rows | Mode-omit | Compressed mode |
| P4.1 Slice plan | Done | This file |
| P4.2 Primary evidence | Done | Scoring parity + popularity-beats-baseline acceptance |
| P4.3 Implementation | Done | `clients/tinder-admin/**` |
| P4.4 Test levels | Done | Unit/component: Vitest. IT/contract/e2e/specialist N/A — no HTTP, DB, or deploy |
| P4.5 Error-path evidence | Done | Empty nearby deck skips the viewer; canvas missing throws |
| P4.6 Review | Done | Explicit self-review after tests/build |
| P4.7 Targeted defect review | Done | No confirmed product defect. Popularity needed a second pass and an incoming-like queue to convert likes to matches |
| P4.8 Quality gates | N/A | Policy CI unchanged; no Java/Go/Python surface |
| P4.9 Handoff | N/A | Same session |
| P4.10 Combined-diff review | Mode-omit | One-slice mode |
| P5.1 Build | Done | `npm test` 9/9; `npm run build` |
| P5.2 Security scan | N/A | Static SPA, no new network calls, no secrets |
| P5.3 Deploy | N/A | Not authorized; local `npm run dev` only |
| P5.4 Smoke | Done | Browser Play on Vite preview |
| P5.5 Rollback | Done | Revert the commit |
| P5.6 Monitoring | N/A | Not shipped to an origin |
| P5.7 Docs | Done | This file, admin README, demo index, root README |
| P5.8 Open risk | Done | None in scope. AWS stand remains a later, separate run |

