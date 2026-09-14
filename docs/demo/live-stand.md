# Live stand runbook

Production product URL: **https://lunari.misyk.tech**
Identity: **https://auth.misyk.tech** · realm `spring` · client `tinder-client`

Passwords never go in git. Keep the two demo accounts in a password manager.

## Probe (2026-09-14)

From this environment both public hosts answered Cloudflare **522** (origin not reachable) in ~19s:

```text
curl -sS -o /dev/null -w '%{http_code} %{time_total}\n' https://lunari.misyk.tech
# 522 19.67

curl -sS -o /dev/null -w '%{http_code} %{time_total}\n' https://auth.misyk.tech/realms/spring
# 522 19.32
```

Do **not** send the live URL to a recruiter while this probe fails. Use the recorded walkthrough and, if needed, local Compose (`docker-compose.yml` + `docker-compose.local.yml`). After the origin is back, re-run the preflight below and only then treat Lunari as the primary demo.

## Demo accounts

Use two prepared users. Suggested labels (replace with the real usernames you created):

| Role in the script | Keycloak username | Must have before the call |
|---|---|---|
| Account A (you) | *(owner-held)* | Complete profile, at least one photo, location set |
| Account B (the match) | *(owner-held)* | Same, and **not** already mutually matched with A *or* keep a spare unused pair |

Do not sign up live. Public registration on a dating stand collects spam and destroys the scripted pair.

Suggested realm roles:

- A and B: `USER_BASIC`
- Optional premium story: one account with `USER_PREMIUM` already granted
- Do not depend on creating an `ADMIN` role during the call

## State the deck must have

Both profiles must have gone through **POST /api/v1/profiles** (or a later update) so `profile.created` / `profile.updated` left the Profiles outbox. SQL inserts leave Deck Read at `202 BUILDING` — see [seed-notes.md](seed-notes.md).

Before the call:

1. A can see B on Discover, or the opposite, or both see a third prepared profile.
2. There is either a fresh pair (no mutual like yet) or a spare pair you can switch to.
3. Chat for an existing match opens in under a few seconds.

## Two-minute preflight

Do this immediately before the interviewer joins.

1. `curl -sS -o /dev/null -w '%{http_code}\n' https://lunari.misyk.tech` — expect `200`, not `522` / `5xx`.
2. `curl -sS https://auth.misyk.tech/realms/spring` — JSON with `"realm":"spring"`.
3. Browser: log in as A, open Discover. The card request is `GET /api/v2/deck` through the gateway. Fail if you see 401 or 500.
4. Incognito (or a second browser): log in as B. Confirm B’s Discover or Matches still loads.
5. If you will show chat, open an existing conversation once so images and history are warm.
6. If you will show premium / likes-you, confirm that account already has `USER_PREMIUM`. Do not start Stripe Checkout cold on a call.

Unauthenticated `GET https://lunari.misyk.tech/api/v2/deck` should be **401**, not 500. 500 after a Deck Read recreate usually means the gateway still holds a stale Deck Read address — restart `gateway`.

## Operational traps

| Symptom | Likely cause | What to do on a call |
|---|---|---|
| Cloudflare 522 / timeout | Origin or tunnel down | Switch to the backup recording. Do not debug live. |
| Discover 500 after a Deck Read rebuild | Gateway Netty DNS cache ([bug note](../features/local-compose/bugs/gateway-stale-deck-read-ip-500.md)) | Restart `gateway`. Script fallback: open Matches / chat. |
| Discover stuck on “preparing your deck” / 202 | Missing `profile.created` projection, or ensure still building | Say “read model is catching up” and switch to a prepared match. |
| Login loop / issuer mismatch | Token `iss` is `https://auth.misyk.tech/realms/spring` but a service still expects the internal Keycloak URL | Do not hot-edit config on a call. Use the recording. |
| Premium stays basic after Checkout | Webhook never reached subscriptions | On localhost use `POST /api/v1/billing/sync`. On prod, only show premium if the role is already there. |
| Empty deck | Pair already swiped, or seed skipped the event path | Refresh once. If empty, switch accounts or open Matches. |
| Ranking admin 403 | Gateway expects realm role `ADMIN`; the realm may only have `USER_ADMIN` | Out of demo scope. Do not open admin. |

## Local fallback

Only when the public origin is down and you still want a live click-path:

```bash
./certs/generate-docker-certs.sh
cp .env.prod.example .env   # fill secrets locally; never commit
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Compose runs **swipes-go**, not `swipes-demo`. After `--no-deps` recreate of `deck-read-api`, restart `gateway`.

The Angular design-preview configuration (`npm run start:preview` in `clients/tinder-client`) can show Discover / Matches / Chat / Profile without the stack. Use it for screenshots and a UI backup, not as proof that Kafka or outbox work.
