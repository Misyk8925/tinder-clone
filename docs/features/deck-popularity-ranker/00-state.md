# State: deck-popularity-ranker

Repo fallback approval log. The project uses Linear, but the integration is not authorized
in this session. Current phase: **5 — release** · 2026-09-10

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 Concept | [`01-concept.ru.md`](01-concept.ru.md) | owner | 2026-09-10 |
| 4 Slice 1 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-10 |
| 4 Slice 2 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-10 |
| 4 Slice 3 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-10 |
| 5 Release | [`05-release/checklist.md`](05-release/checklist.md) | smoked locally; R15 open | 2026-09-10 |
| 4 Slice 3 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-10 |

## Decisions already taken

| ID | Decision | Source |
|---|---|---|
| Q10 | Destination is a learned ranker, embeddings included | owner, 2026-09-10 |
| Q11 | Popularity damped with `log(1+likes)`, plus a newcomer quota | owner, 2026-09-10 |
| Q12 | The counter decays over time | owner, 2026-09-10 — **superseded by measurement**: replaced with a smoothed like rate, which is tenure-independent by construction. Needs owner sign-off at the gate |
| Q13 | Popularity first; embeddings wait for a measured density trigger | owner, 2026-09-10, after the screen put the crossover near 8 observations per profile |
| — | The counter lives in deck's Redis | fact: `--appendonly yes --appendfsync everysec` on the `redis-data` volume |
| — | No new topic or consumer is needed | fact: `SwipeEventConsumer` already receives swiper, swiped and decision |
| — | Rank by a smoothed like rate, not a decayed count | measurement, 2026-09-10: 18.14 matches per 100 swipes against 13.36, and appeal recall 64.6% against 29.4% |
| — | Newcomer quota is 12%, interleaved through the deck | measurement, 2026-09-10: matches peak on a 10-12% plateau and coverage keeps rising; slots appended past the window are never reached |

Approved by the owner on 2026-09-10, including the reversal of Q12: no decay job ships, the
like rate removes incumbency by construction.

## Risks

| ID | Risk | State |
|---|---|---|
| R1 | The decay half-life | **Closed 2026-09-10** — the half-life sweep was monotone, which was the symptom of measuring the wrong quantity; a smoothed like rate doubles appeal recall and needs no half-life |
| R5 | Appeal drift over time is not simulated, so whether decay is still wanted on top of the rate is unknown | Open — decided not to add it until live data says otherwise |
| R2 | The newcomer quota size | **Closed 2026-09-10** — measured over six self-training rounds; a 10-12% plateau, 12% taken for its wider counter coverage |
| R3 | The screen's absolute levels are optimistic | Accepted — the comparison between arms is the robust part |
| R4 | Losing Redis silently degrades ranking to `baseline` | Mitigated — FR-6 backfill, NFR-3 safe degradation |

| R6 | Seeded profiles never emitted `profile.created`, so deck-read's projection has no entry for them and every deck stays `202 BUILDING` | Open — seeding through the event path was never scoped into a slice |

Bugs: two found by the live run and fixed the same day — `swipe_events` has no writer, and the backfill exceeded WebClient's buffer limit. Both recorded in the release checklist.
All three slices are built and every FR and NFR in the concept is covered by tests.

Next: phase 5. Nothing here has run against a live stack — the `popularity` variant has never
served a real deck, the rebuild has never called the real consumer, and the density endpoint
has never been reached through the gateway. Before any of that:

- `DECK_DB_PASSWORD` in `.env`, and on an existing postgres volume `deck_db`, the `deck_app`
  role and its grants created by hand.
- A Keycloak user with the `ADMIN` realm role, and `ADMIN_APP_ORIGIN` set to the admin app's
  real origin.
- The `popularity` variant is inert until an experiment in the registry assigns viewers to
  it; the shipped default stays `baseline`.
