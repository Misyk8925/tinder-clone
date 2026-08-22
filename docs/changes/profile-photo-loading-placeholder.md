# Profile photo loading placeholders

## Compact scope record

- Intended outcome: Profile hero and manage-photos thumbs show a loading placeholder until the image is visible, including while a JPEG/PNG is compressing or uploading. Refreshing the profile must not flash already-shown photos back to pending when a new signed URL arrives.
- Out of scope: chat photos, discover/swipe cards, profile edit form, backend storage, presign TTL.
- Affected contract: none.
- Observable evidence:
  - Given a profile photo whose image has not loaded, when Profile renders, then a skeleton placeholder is shown over the hero.
  - Given that image fires `load` or is already `complete`, when Profile updates, then the skeleton is gone.
  - Given a displayed photo, when getMe returns a new signed URL, then the displayed src is kept.
- Selected checks: `npx vitest run src/app/core/utils/profile-photo-merge.spec.ts src/app/features/profile/profile.component.spec.ts`; `git diff --check`.
- Rollback: revert profile component, `styles.scss` photo-frame rules, merge util, and specs.
- Promotion check: one reversible frontend-only UI slice; no contract or owner decision.

## Evidence

- Vitest: 6 passed.
- `git diff --check` passed.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: same profile-page UI slice as chat photos |
| R.2 Project profile | Done | Compact `docs/changes` record; Vitest |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Compressed mode |
| C.1 Outcome / out of scope | Done | Recorded above |
| C.2 Affected contract | Done | none |
| C.3 Acceptance check | Done | Merge + profile component Given/When/Then specs |
| C.4 Test levels and commands | Done | Commands above |
| C.5 Rollback | Done | Revert listed client files |
| C.6 Promotion check | Done | Still one low-risk UI slice |
| P4.1 Slice plan | Done | Skeleton until ready; keep displayed src; immediate jpeg blob |
| P4.2 Primary evidence | Done | Specs |
| P4.3 Implementation | Done | `profile.component.ts`, `styles.scss`, `profile-photo-merge.ts` |
| P4.4 Test levels | Done | Component + unit. Integration/e2e N/A: presentation only |
| P4.5 Error path | Done | Failed upload reloads server photos; img error hides skeleton |
| P4.6 Review | Done | Explicit self-review; no independent reviewer |
| P4.7 Targeted defect review | Done | HEIC still waits for conversion; overlay only while preview is not ready |
| P4.8 Quality gates | Done | Vitest + `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | Focused Vitest |
| P5.2 Security scan | N/A | Same photo URLs; blob previews revoked on destroy |
| P5.3 Deploy | Done | Local `tinder-client` rebuild |
| P5.4 Smoke | Done | Specs cover hero skeleton, load, and complete |
| P5.5 Rollback | Done | Revert the listed files |
| P5.6 Monitoring | Done | Not yet observed live beyond specs |
| P5.7 Docs | Done | This compact record |
| P5.8 Open-risk view | Done | No in-scope blocker |
