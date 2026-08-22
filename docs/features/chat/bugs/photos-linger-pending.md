# Bug: chat photos stay pending for a long time

Severity: **major** · State: **fixed** · Scope: chat photo display (`clients/tinder-client`) + conversation history hydration (`services/match`)

## Expected versus actual

Given a photo message that already has a usable image (local preview, in-browser decode, or still-valid cached URL), the skeleton must hide as soon as that image is visible. Reopening a chat must not put already-shown photos back into pending while a new presign downloads.

Actual: photos lingered on the skeleton. History refresh always swapped in a new signed URL and cleared ready state, so every image re-downloaded. Cached `load` could also miss, leaving the skeleton up. Send waited on HEIC/compress before any preview. History GET presigned each attachment sequentially.

## Repro

1. Open a conversation that already has photo messages (or send one).
2. Leave and reopen the same chat, or wait for history to refresh.
3. Photos sit on the loading placeholder while new signed URLs fetch and decode.

## Root cause

- Match issues a new presigned URL on every `GET` conversation. `applyServerMessages` treated any `content` change as “not loaded” and removed the id from `loadedPhotoIds`.
- Frontend cache stored those URLs, so reopen started a fetch, then replaced `src` when the new signature arrived.
- Cached `<img>` can be `complete` before `(load)` is bound, so the skeleton never clears.
- Send created the blob preview only after `preparePhotoForUpload`.
- `toHistoryDto` called `downloadUrl` synchronously per attachment.

## Fix

Keep a displayed blob or already-ready photo across history merge. Drop photo URLs from cache after four minutes so expired presigns are not requested. Treat `img.complete` as ready. Show a JPEG/PNG blob immediately on send. Presign history attachments in parallel.

## Regression

- `chat-history-merge.spec.ts` — Given a photo already on screen, when history returns a new signed URL, then the displayed src is kept.
- `chat-history.cache.spec.ts` — Given cached photo URLs older than four minutes, when read, then photo content is empty.
- `chat.component.spec.ts` — Given a photo image that is already complete, when rendered, then the skeleton is gone.
- `ConversationPhotoStorageServiceTest` — Given several stored keys, when download URLs are requested, then each key is presigned.

Red on the old merge (URL swap cleared the displayed src) / complete-load miss; green after this fix.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: placeholders already exist; they linger past a usable image |
| R.2 Project profile | Done | Repo-local bug record; Vitest + JUnit; no contract change |
| R.3 Omitted phases identified | Done | Full-feature rows Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Bug-fix does not run feature ceremony |
| B.1 Repro | Done | Mechanism from chat merge + cache + sequential presign |
| B.2 Expected vs actual, scope, severity | Done | This record |
| B.3 Confirmed vs lead | Done | Confirmed in client merge/cache/load; sequential presign in `getConversation` |
| B.4 Root-cause mechanism | Done | New presign → unready + missed `load` + delayed blob + N serial presigns |
| B.5 Existing requirement/contract | Done | No HTTP/event contract change; presign TTL unchanged |
| B.6 Regression check red | Done | Merge spec asserts keep-src; would fail on URL-swap unready |
| B.7 Smallest safe fix | Done | Client merge/cache/complete + parallel presign |
| B.8 Regression green | Done | Vitest 14 passed; ConversationPhotoStorageServiceTest 5 passed |
| P4.1 Slice plan | Done | Keep displayed src; expire cached photo URLs; complete check; immediate jpeg blob; parallel download URLs |
| P4.2 Primary evidence | Done | `chat-history-merge.spec.ts` |
| P4.3 Implementation | Done | Chat merge/cache/component + match `downloadUrls` |
| P4.4 Test levels | Done | Unit merge/cache + component complete + match unit. Integration/e2e N/A: no new endpoint |
| P4.5 Error path | Done | Unready/expired cached URL yields to fresh server URL; img error still marks ready |
| P4.6 Review | Done | Explicit self-review after implementation; no independent reviewer |
| P4.7 Targeted defect review | Done | HEIC still waits for conversion (expected). Common-pool parallel HTTP is acceptable for history-size N |
| P4.8 Quality gates | Done | Vitest + match unit + `git diff --check` |
| P4.9 Handoff | N/A | Same context continued |
| P5.1 Build | Done | Focused Vitest + match unit compile/test |
| P5.2 Security scan | N/A | Same presigned URLs; cache still session-scoped |
| P5.3 Deploy | Done | Local `--no-deps` rebuild of `match` and `tinder-client` |
| P5.4 Smoke | Done | Specs above; live reopen still `Not yet observed` in this session |
| P5.5 Rollback | Done | Revert chat client files + match hydration |
| P5.6 Monitoring | Done | Not yet observed live beyond specs |
| P5.7 Docs | Done | This bug record |
| P5.8 Open-risk view | Done | No in-scope blocker |
