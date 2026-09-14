# Backup recording

A six-minute **live-origin** recording is the recruiter backup once `https://lunari.misyk.tech` is up. Follow [script.md](script.md). Publish unlisted YouTube or Loom and paste the URL into the root README.

Until then, this change includes a **design-preview** UI walkthrough captured 2026-09-14 against `npm run start:preview` on `http://127.0.0.1:4200/`. That client uses in-memory fixtures (`designPreviewInterceptor`). It proves the Discover / Likes / Messages / Chat / Profile surfaces, not Kafka, outbox, or mTLS.

Phone-width stills (390px, bottom tabs): [screenshots/mobile/](screenshots/mobile/). Desktop stills stay in [screenshots/](screenshots/).

| Item | Value |
|---|---|
| Preview walkthrough | Attached to the pull request as `lunari_preview_ui_walkthrough.mp4` (~1:40). Sequence: Discover (Mila) → Likes → Messages → chat with Mila → Profile (Michael, 27) → Discover. |
| Stills | [screenshots/](screenshots/) |
| Live origin at capture time | Cloudflare 522 on lunari and auth — see [live-stand.md](live-stand.md) |

## Owner steps before an interview

1. Restore the origin and run the two-minute preflight in [live-stand.md](live-stand.md).
2. Record the six-minute path from [script.md](script.md) on the live stand (two real accounts, not preview).
3. End with ~30 seconds on `ProfileOutboxBatchProcessor` or `DeckReadCqrsBoundaryAcceptanceTest`.
4. Upload unlisted. Replace the “Backup recording” row in the root README with that URL.
