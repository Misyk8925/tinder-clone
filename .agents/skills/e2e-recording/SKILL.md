---
name: e2e-recording
description: Record browser tests of the deployed Lunari app (lunari.misyk.tech) on the VPS; every run is archived and linked from its pull request. Use when a change needs end-to-end proof in a real browser, when asked to test sign-in/sign-out or multi-account flows, to reset or recreate the e2e test accounts (e2e-a, e2e-b), or to find the video, trace and report of an earlier run for a PR or commit. Runs only on the VPS host vmi3773346 as user dev.
---

# Recorded e2e runs (Playwright, VPS)

Every run is recorded in full: one video per browser session (cursor and current step drawn in),
a Playwright trace, screenshots, and an HTML report. **Every run is archived** on the VPS for
**90 days** and the pull request gets a comment that links each run's report and every video. The
pace is a person's (slowMo + reading pauses + key-by-key typing), so a run takes minutes, not
seconds; do not try to speed it up for real runs.

## Run

Always use `e2e-record`, from a checkout of the branch under test (it must contain `e2e/package.json`).
Never run Playwright directly or write recordings only to `e2e/out/`: such runs are not archived
and have no link anyone else can open.

```bash
e2e-record                                       # tests with the existing test accounts
e2e-record --accounts fresh                      # delete both accounts, register them again, then test
e2e-record --accounts delete                     # only delete both accounts completely
e2e-record --accounts recreate                   # delete, then register again; no other tests
e2e-record -- --grep "two browsers"              # pass args to `playwright test` after --
e2e-record --attach <PR number>                  # no run: rebuild the PR comment from archived runs
```

- PR: `e2e-record` finds the open PR of the current branch through `gh`; `--pr <n>` overrides it.
  No PR yet is fine: the run is archived under its commit (`commit-<sha8>`). Push and open the PR,
  then run `e2e-record --attach <n>`: the comment collects runs of the PR and of all its commits.
- One comment per PR (marked `<!-- e2e-recordings -->`), updated in place on every run, newest
  first. Do not paste the same links into the PR again; link the comment if needed. `--no-comment`
  archives without touching the PR.
- Default target is production `https://lunari.misyk.tech`; `--base-url https://host` for another origin.
- Before the first run of a session the image `mcr.microsoft.com/playwright:v1.65.1-noble` may be
  pulled (2.6 GB, several minutes). Runs use dev's rootless Docker, 2 GB / 2 CPU, one browser.
- Exit code 0 = all passed. The last lines print the report link and one link per video.

## Results

- Report for humans (tailnet only): `https://vmi3773346.tailf8b6f4.ts.net:8445/<group>/<run-id>/report/index.html`
  where `<group>` is `pr-<n>` or `commit-<sha8>`. Index of all runs: `https://vmi3773346.tailf8b6f4.ts.net:8445/`
  (`#pr-<n>` / `#commit-<sha8>` filter it). Videos are `report/data/<hash>.webm`; `videos.json`
  in the run says which test each one belongs to.
- You cannot open that URL from the VPS (only root may connect locally). Read the files instead:
  `/srv/e2e-recordings/<group>/<run-id>/` → `results.json` (statuses, errors), `metadata.json`,
  `videos.json`, `report/data/*` (videos `.webm`, traces `.zip`, screenshots). They are root-owned
  and read-only: you can add runs, never change or delete archived ones.
- A run appears in the archive about a second after the test finishes. A run rejected by the
  archiver (symlinks, foreign files, > 3 GB) is dropped; ask the owner to check `.rejected.log`.

## Test accounts

- `e2e-a` and `e2e-b` (Keycloak realm `spring`, e-mail `@lunari.test`, role USER_BASIC). Credentials
  are in `~/.config/e2e/accounts.env` and reach the container via `--env-file`. **Never print, copy,
  commit or paste them**; refer to them as `E2E_USER_A_*` / `E2E_USER_B_*`.
- "Full deletion" (`--accounts delete|recreate|fresh`) is: sign in → Profile → "Delete account" in the
  app (the app's own soft delete + ProfileDeleted event), then root's `e2e-accounts-reset` deletes the
  Keycloak users. Root only ever deletes the allowlisted `e2e-a`/`e2e-b` with an `@lunari.test` e-mail.
  Registration then goes through Keycloak's "Create account" form, exactly like a new user.
- Need a third account? Ask the owner: the allowlist lives in `/usr/local/sbin/e2e-accounts-reset`.
- Never use real users' accounts, and never create users through the Keycloak admin API yourself.

## Writing tests

- Specs live in `e2e/tests/*.spec.ts`; account lifecycle specs in `e2e/tests/accounts/` run only
  through `--accounts`. Import `test`/`expect` from `e2e/support/fixtures`.
- Act like a user: `login`, `logout`, `register`, `deleteAccountInApp`, `expectSignedInAs` from
  `support/auth`; `think(page, 'glance'|'read'|'decide')` between actions and `typeLikeHuman()` for
  input, from `support/human`. Use role/label locators, not CSS.
- Multi-account: same browser = `login(A)` → `logout` → `login(B)` on one `page`;
  simultaneously = `const pageB = await openSession('B')` (separate cookies, own video).
- Wrap each user-visible stage in `test.step('…')`: the step title is drawn into the video.
- Tests run against production. Leave data as you found it (delete what you create), never pay,
  subscribe, message or like real users' profiles, and keep to the two test accounts.

## When something fails

1. Read `results.json` (error message, failing step), then the trace `report/data/*.zip`
   (`npx playwright show-trace <zip>` locally if needed).
2. A selector change in the app is a test bug: fix the spec in the same PR.
3. Infra problems (Docker, archive, account reset) are not yours to fix on the host: report the exact
   error to the owner. Do not edit anything under `/usr/local`, `/etc/systemd` or `/srv`.
