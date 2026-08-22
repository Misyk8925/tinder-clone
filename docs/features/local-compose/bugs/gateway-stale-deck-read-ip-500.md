# Gateway returns 500 for `/api/v2/deck` after a `--no-deps` Deck Read rebuild

Severity: **major** · State: **fixed** · Scope: local Compose gateway → `deck-read-api`

## Expected versus actual

Given a healthy `deck-read-api`, `GET /api/v2/deck` through the gateway must reach Deck Read (401 without a token, 200/202 with one).

Actual: gateway logged `500 Server Error for HTTP GET "/api/v2/deck?limit=20"` with `Connection refused: deck-read-api/172.23.0.13:8040` while the live container was `172.23.0.16` and `/q/health` was `UP`.

## Repro

1. Rebuild only Deck Read: `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build --no-deps deck-read-api deck-read-worker`
2. Keep the long-lived `gateway` container.
3. Reload Discover.

Observed 2026-08-22 18:56Z: repeated gateway 500s to the previous IP; `getent hosts deck-read-api` inside gateway already showed the new IP.

## Root cause

Reactor Netty in the gateway caches the resolved address. `--no-deps` recreate changes the Deck Read container IP; Docker DNS updates, but the gateway process keeps connecting to the dead address. Deck Read itself did not throw.

## Fix

Restart `gateway` after a `--no-deps` Deck Read recreate so the HTTP client resolves again.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: live 500 vs established deck read path |
| R.2 Project profile | Done | Repo bug file under `docs/features/local-compose/bugs` |
| R.3 Omitted phases | Done | P1–P3 Mode-omit |
| P1.1–P1.12, P2.1–P3.7, P4.10 | Mode-omit | Bug-fix mode |
| B.1 Repro | Done | Gateway logs `0a50b9d0-77*` + IP `172.23.0.13` vs live `172.23.0.16` |
| B.2 Expected vs actual | Done | Above |
| B.3 Confirmed vs lead | Done | Confirmed from gateway + inspect |
| B.4 Root-cause mechanism | Done | Stale Netty DNS after `--no-deps` recreate |
| B.5 Contract inspected | Done | No HTTP contract change; Deck Read was healthy |
| B.6 Regression red | N/A | Operational DNS cache; no faithful in-process test without IP churn |
| B.7 Smallest safe fix | Done | `docker compose ... restart gateway` |
| B.8 Regression green | Done | After restart: gateway→API `/q/health` UP; public `GET /api/v2/deck` is `401 UNAUTHENTICATED`, not 500 |
| P4.1–P4.9 | Done | Operational fix; no gateway code change |
| P5.1 Build | N/A | No artifact rebuilt |
| P5.2 Security scan | N/A | Restart only |
| P5.3 Deploy | N/A | Local restart |
| P5.4 Smoke | Done | 401 on unauthenticated deck; Deck Read health UP |
| P5.5 Rollback | N/A | Restart has no residual change |
| P5.6 Monitoring | N/A | Not a production deploy |
| P5.7 Docs | Done | This file |
| P5.8 Open-risk | Done | No blocker; next `--no-deps` Deck Read rebuild must restart gateway again |
