# Recorded e2e tests

Playwright tests that drive the deployed app the way a person does (slowMo, reading pauses,
key-by-key typing) and record everything: one video per browser session with the cursor and the
current step drawn in, a trace, screenshots and an HTML report.

They are run by agents on the VPS with `e2e-record`, which archives each run under its pull
request for 90 days. Agent instructions: [`.agents/skills/e2e-recording/SKILL.md`](../.agents/skills/e2e-recording/SKILL.md).
Server side (archiver, viewer, account reset, retention): [`ops/e2e-recordings/`](../ops/e2e-recordings/).

```bash
e2e-record --pr 123                     # tests with the existing e2e-a / e2e-b
e2e-record --pr 123 --accounts fresh    # delete both accounts fully, register again, then test
```

Local run against production (needs the two accounts in your environment):

```bash
cd e2e && npm ci && npx playwright install chromium
E2E_PACE=fast npx playwright test smoke
```

| Variable | Default | Meaning |
| --- | --- | --- |
| `E2E_BASE_URL` | `https://lunari.misyk.tech` | App origin under test |
| `E2E_SLOWMO_MS` | `0` | Extra delay Playwright adds to every input event (typing pays it twice per key) |
| `E2E_PACE` | `user` | `fast` shrinks the human pauses for local iteration |
| `E2E_ACCOUNTS` | `keep` | `delete`, `recreate`, `fresh`: account lifecycle projects (see `playwright.config.ts`) |
| `E2E_USER_{A,B}_USERNAME` / `_PASSWORD` / `_EMAIL` | — | Test accounts; e-mail defaults to `<username>@lunari.test` |
