# Tinder Admin — ranking experiments

Operator-only single-page app for the ranking experiment registry owned by
[deck](../../services/deck/README.md). Deliberately separate from
[tinder-client](../tinder-client/README.md): it shares no bundle, no route and no
Keycloak client with the user-facing application.

Parent map: [Lunari README](../../README.md) · Domain: clients · State:
[deck-ranking-experiments](../../docs/features/deck-ranking-experiments/00-state.md)

## Boundary

**Owns.** List / create-draft / start / stop for ranking experiments. Courtesy `ADMIN`
check before render.

**Does not own.** The experiment store or scoring ([deck](../../services/deck/README.md)).
The real authorization gate (`hasRole("ADMIN")` on `/api/v1/admin/**`). Outcome screen
(slice 5). User-facing Discover.

**Neighbors.** Nginx proxies `/api/` to [gateway](../../services/gateway/README.md).
Compose publishes no host port.

## What it does

Lists experiments, creates a draft, starts one and stops it. Nothing else — the outcome
screen arrives with slice 5.

## How access works

- The app requires a Keycloak session (`login-required`) and checks the `ADMIN` realm role
  before it renders anything.
- That check is a courtesy, not the control. The real gate is the gateway, which already
  guards `/api/v1/admin/**` with `hasRole("ADMIN")` in `SecurityConfig`.
- nginx proxies `/api/` to the gateway, so the browser stays on one origin and no CORS
  origin has to be opened for this app.
- The compose service publishes no port. Reach it over the compose network, an SSH tunnel,
  or `docker compose port`.

## Configuration

`public/config.json` is read at startup and served with `no-store`, so Keycloak settings
can be changed by replacing that one file — no rebuild. `GATEWAY_URL` is substituted into
the nginx config when the container starts.

## Local development

```bash
npm install
GATEWAY_URL=http://localhost:8222 npm run dev
```
