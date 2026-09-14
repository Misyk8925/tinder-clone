# Seeding a demo deck through events

Deck Read materialises cards from **profile events**, not from a SQL dump. If you insert into `profiles` and skip the outbox, every Discover call stays `202 BUILDING`. That failure is recorded as risk R6 on the popularity-ranker work; it applies to any seed.

There is no recruiter seed script in this repository. A large population generator (`services/deck/load-tests/deck-sim`) is not in the current tree. Use the public Profiles API so `PROFILE_CREATED` is written in the same transaction as the row.

## Required path

```text
Keycloak user
  → browser or curl with a user JWT
  → Gateway
  → POST /api/v1/profiles
  → Profiles DB commit + outbox row (PROFILE_CREATED)
  → ProfileOutboxBatchProcessor publishes profile.created
  → Deck / Deck Read consumers project the card
  → GET /api/v2/deck returns READY items
```

The outbox write lives in `ProfileOutboxService`. The publisher is `ProfileOutboxBatchProcessor`. Topic name defaults to `profile.created` (`KafkaTopicProperties`).

Later edits must go through `PUT` / `PATCH /api/v1/profiles` so `profile.updated` invalidates the right reverse indexes. Deletes must go through the API as well.

## Do not

- `INSERT INTO profiles …` or restore a dump and expect Discover to work.
- Point Deck Read at a generated id that never appeared on `profile.created`.
- Seed only one gender / city / age band and then wonder why the other account’s deck is empty.
- Commit `.env`, Keycloak passwords, or Stripe keys next to the seed notes.

## Minimum population for the six-minute script

1. Create two Keycloak users in realm `spring` (A and B) with `USER_BASIC`.
2. Sign in as A. Complete profile + one photo + location through the UI (or `POST /api/v1/profiles` then the photo upload the client already uses).
3. Sign in as B. Same.
4. Wait until `GET /api/v2/deck` for A is **200** with B (or another prepared card) in `items`, not 202.
5. Leave them unmatched, or keep a third user C as a spare card.

Photos go through the Photos service. Profiles and Match never serve raw bytes; a profile without a completed upload looks broken on the card even if the event landed.

## curl sketch (local or prod gateway)

Replace tokens and names. Do not paste a live token into the repo.

```bash
# 1. Obtain a user access token from Keycloak (password grant only on a private stand).
# 2. Create the profile — this must hit the service so the outbox row exists.

curl -sS -X POST "$GATEWAY/api/v1/profiles" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ada",
    "age": 28,
    "gender": "female",
    "bio": "Demo profile A",
    "city": "Vienna",
    "preferences": {"minAge": 24, "maxAge": 36, "gender": "all", "maxRange": 80}
  }'
```

Then poll as the other user:

```bash
curl -sS -D - "$GATEWAY/api/v2/deck?limit=20" \
  -H "Authorization: Bearer $TOKEN_B"
```

Expect `200` and `state: READY` (or the current Deck Read equivalent) with at least one item. `202` means keep waiting or the event never left the outbox. `401` is auth. `500` on a just-recreated Deck Read container is usually a stale gateway DNS entry — restart `gateway`.

## After a database reset

1. Recreate Keycloak users or keep the realm volume.
2. Recreate profiles **through the API**.
3. Do not reuse Redis deck keys from the old generation; let Deck rebuild via `ensure`.
4. Re-run the two-minute preflight in [live-stand.md](live-stand.md).

## Compose reminder

The stack that matches production is `docker-compose.yml` plus `docker-compose.local.yml`. Swipe writes go to **swipes-go**. `swipes-demo` is the Java rollback path, not the seed target.
