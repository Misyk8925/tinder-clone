# Chat image loading placeholders

## Compact scope record

- Intended outcome: photo messages in chat show a loading placeholder on both sides until the image is visible, including while the sender’s upload is in progress.
- Out of scope: lightbox behaviour, photo compression, backend storage, WebSocket payload changes, text messages.
- Affected contract: none.
- Observable evidence:
  - Given a photo message whose image has not loaded, when chat renders it (mine or theirs), then a skeleton placeholder is shown.
  - Given that image fires `load`, when chat updates, then the skeleton is gone and the photo is shown.
  - Given the user picks a photo to send, when compression/upload has not finished, then a mine-side placeholder appears immediately.
- Selected checks: `npx vitest run src/app/features/chat/chat.component.spec.ts`; `git diff --check`.
- Rollback: revert `chat.component.ts` / `chat.component.spec.ts`; no data or API rollback.
- Promotion check: one reversible frontend-only UI slice; no contract or owner decision.

## Evidence

- Vitest: 2 passed.
- `git diff --check` passed.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: one UI slice, no contract |
| R.2 Project profile | Done | Compact `docs/changes` record; Vitest; local Compose |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Compressed mode does not run full-feature ceremony |
| C.1 Outcome / out of scope | Done | Recorded above |
| C.2 Affected contract | Done | none |
| C.3 Acceptance check | Done | `chat.component.spec.ts` Given/When/Then photo placeholders |
| C.4 Test levels and commands | Done | Component: `npx vitest run src/app/features/chat/chat.component.spec.ts` |
| C.5 Rollback | Done | Revert the two chat files |
| C.6 Promotion check | Done | Still one low-risk UI slice |
| P4.1 Slice plan | Done | Skeleton until load; optimistic mine placeholder on send |
| P4.2 Primary evidence | Done | Spec red without `.photo-frame`, green after |
| P4.3 Implementation | Done | `chat.component.ts` |
| P4.4 Test levels | Done | Component selected. Unit/integration/contract/e2e/specialist N/A: visual load state only |
| P4.5 Error path | Done | Failed send removes pending bubble; img error hides skeleton |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | No additional confirmed defect; cached-img `load` miss remains a lead |
| P4.8 Quality gates | Done | Vitest + `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | Focused Vitest |
| P5.2 Security scan | N/A | Presentation-only; no new input surface |
| P5.3 Deploy | Done | Local `tinder-client` rebuild when requested by live stack |
| P5.4 Smoke | Done | Spec covers both sides + load completion |
| P5.5 Rollback | Done | Revert chat component files |
| P5.6 Monitoring | Done | Not yet observed beyond the spec |
| P5.7 Docs | Done | This compact record |
| P5.8 Open-risk view | Done | No in-scope blocker |
