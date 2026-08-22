# Frontend chat history cache

## Compact scope record

- Intended outcome: reopening a conversation shows cached messages immediately while history refreshes from the match API. No new Redis or backend cache.
- Out of scope: offline send, pagination, caching the matches list, changing photo presign TTL, Service Worker/image cache.
- Affected contract: none.
- Observable evidence:
  - Given a conversation was loaded once, when it is opened again, then cached messages are returned without waiting on HTTP.
  - Given blob: photo previews, when written to the cache, then they are omitted.
  - Given two profile IDs, when they cache the same conversation id, then their histories stay isolated.
- Selected checks: `npx vitest run src/app/core/services/chat-history.cache.spec.ts src/app/features/chat/chat.component.spec.ts`; `git diff --check`.
- Rollback: revert the cache service, chat wiring, and logout clear.
- Promotion check: one reversible in-memory/session cache; photo URLs are refreshed from the network so expired presigns are not a product decision.

## Evidence

- Vitest: 7 passed.
- `git diff --check` passed.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: frontend cache only |
| R.2 Project profile | Done | Compact `docs/changes` record; Vitest |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Compressed mode |
| C.1 Outcome / out of scope | Done | Recorded above |
| C.2 Affected contract | Done | none |
| C.3 Acceptance check | Done | Cache + chat component Given/When/Then specs |
| C.4 Test levels and commands | Done | Unit + component: vitest commands above |
| C.5 Rollback | Done | Revert cache service, chat wiring, logout clear |
| C.6 Promotion check | Done | Still one low-risk UI cache slice |
| P4.1 Slice plan | Done | Memory + sessionStorage; stale-while-revalidate |
| P4.2 Primary evidence | Done | Specs fail without cache read on open |
| P4.3 Implementation | Done | `chat-history.cache.ts` + chat/keycloak wiring |
| P4.4 Test levels | Done | Unit cache + component open-from-cache. Integration/contract/e2e N/A: no API change |
| P4.5 Error path | Done | Blob URLs omitted; HTTP failure keeps cached thread; logout clears |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | No additional confirmed defect |
| P4.8 Quality gates | Done | Vitest + `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | Focused Vitest |
| P5.2 Security scan | N/A | Session-scoped; cleared on logout; keyed by profileId |
| P5.3 Deploy | Done | Local `tinder-client` rebuild |
| P5.4 Smoke | Done | Specs: instant cached render + isolation |
| P5.5 Rollback | Done | Revert the three client files |
| P5.6 Monitoring | Done | Not yet observed beyond the spec |
| P5.7 Docs | Done | This compact record |
| P5.8 Open-risk view | Done | No in-scope blocker |
