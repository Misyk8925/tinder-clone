# Bug: profile and deck photos 403 against a private S3 bucket

Severity: **blocker** · State: **fixed** · Scope: Profiles GET photo `url`, Deck Read GET `/api/v2/deck` photo `url`, match chat attachment `url`

## Expected versus actual

Given photos are uploaded with IAM `PutObject` and the bucket has **Block all public access**, client `<img src>` must receive a time-limited presigned GET (or CloudFront+OAC). `SharedPhotoDto.url` is documented as a pre-signed or CDN URL.

Actual: Profiles and Deck Read return the stored public object URL (`https://{bucket}.s3.{region}.amazonaws.com/photos/...` or CloudFront). The Angular client binds that string directly. With public access blocked and no OAC, the browser gets **403**.

## Repro

1. Local stack with a private S3 bucket (Block all public access on).
2. Sign in, upload a profile photo, open Profile or Discover.
3. The `<img>` request to the stored URL returns 403. Photos `GET /api/v1/photos/{id}/download-url` already returns a working presigned URL, but nothing in the client-facing read path uses it.

## Root cause

Upload persists Photos `public_url` on `photo.url`. Query mappers (`SharedPhotoMapper`, `JpaProfileQueryAdapter`, Deck Card projection / Redis) copy that stored URL into JSON. Signing exists only on the owner-scoped download-url endpoint, which cannot be used for other people's deck/likes/matches photos. Deck Redis must keep a stable key/URL; a presign written at projection time would expire in ~5 minutes.

## Fix

Keep JSON field `url`. Hydrate a fresh presigned GET at **read** time from `s3Key` (Profiles) or by parsing the stored object path (Deck Read, match chat). Do not presign in `DeckCardProjectionFactory`.

## Regression

- `SharedPhotoMapperTest` / `JpaProfileQueryAdapterPhotoUrlTest` — Given a catalogued photo stored as a public S3 URL, When the profile is mapped for a client, Then `url` is a presigned GET.
- `DeckPhotoUrlRewriterTest` — Given a cached public photo URL, When a deck page is rewritten, Then `url` is replaced by the photos service presign.
- Photos `test_given_stored_photos_when_batch_download_urls_are_posted_then_signed_urls_are_returned`.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | bug-fix: established behaviour (presigned/CDN `url`) is violated |
| R.2 Project profile | Done | repo bug md; JUnit/pytest; Maven/Go/npm per AGENTS.md |
| R.3 Mode-omit | Done | P1.1–P1.12, P2.*, P3.*, P4.10 Mode-omit (not a full feature) |
| B.1 Repro | Done | stored `photo.url` bound in `profile.component.ts` / swipe cards; S3 403 with Block public access |
| B.2 Expected vs actual | Done | `url` must be presigned or CDN; actual unsigned public S3 URL |
| B.3 Confirmed | Done | upload stores `public_url`; download-url unused on read path |
| B.4 Root-cause | Done | read mappers copy stored URL; owner-only download-url cannot hydrate others' photos |
| B.5 Contract | Done | JSON field `url` unchanged; additive Photos `POST /download-urls` |
| B.6 Regression red | Done | mapper test expects `X-Amz-Signature` |
| B.7 Smallest safe fix | Done | sign at Profiles/Deck Read/match read; keep Kafka/Redis unsigned |
| B.8 Regression green | Done | mapper/rewriter/photos API tests green |
| P4.1 Slice plan | Done | one slice: hydrate `url` at read |
| P4.2 Primary evidence/red | Done | mapper/rewriter tests |
| P4.3 Implementation | Done | Profiles signer, Photos batch, Deck Read rewrite, match hydrate |
| P4.4 Test levels | Done | unit (Profiles, Deck Read, match) + photos API; e2e login HITL |
| P4.5 Error-path | Done | fail-open to stored URL; photos-down rewriter test |
| P4.6 Review | Done | self-review after context break |
| P4.7 Targeted defect review | Done | no new confirmed defects in this pass |
| P4.8 Quality gates | Done | targeted Maven/pytest; compose rebuild |
| P4.9 Handoff | N/A | same context |
| P4.10 Combined-diff | Mode-omit | one-slice bug-fix |
| P5.1 Build | Done | compose `--build` profiles/photos/deck-read/match |
| P5.2 Security scan | N/A | no new public surface beyond internal batch presign |
| P5.3 Deploy | Done | local compose |
| P5.4 Smoke | Done | stored S3 GET 403; presigned GET 200 JPEG; client :4200 200 |
| P5.5 Rollback | Done | revert read-path hydration; stored objects unchanged |
| P5.6 Monitoring | Not yet observed | local only |
| P5.7 Docs | Done | this bug record |
| P5.8 Open risk | Done | no in-scope blocker remains |
