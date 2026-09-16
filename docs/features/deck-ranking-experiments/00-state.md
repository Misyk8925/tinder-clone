# State: deck-ranking-experiments

Repo fallback approval log. The project uses Linear, but the integration is not authorized
in this session. Current phase: **4 — implementation, slice 1** · 2026-09-09

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 Concept | [`01-concept.ru.md`](01-concept.ru.md) | owner | 2026-09-09 |
| 4 Slice 1 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 2 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 3 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 4a | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 4b | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 5 | [`04-implementation/log.md`](04-implementation/log.md) | complete, unreviewed | 2026-09-09 |
| 4 Slice 5 fix | interval resampled over viewers, not swipes | complete, unreviewed | 2026-09-10 |
| 4 Slice 6a | offline screen; NFR-10 evaluated | complete, unreviewed | 2026-09-10 |

## Answered questions

| ID | Question | Answer |
|---|---|---|
| Q1 | Who does the A/B run on? | Simulated population on the AWS stand |
| Q2 | Where does the admin live? | A separate application |
| Q3 | Order of work | Admin first, then the A/B of baseline vs the reciprocity fix (owner) |

## Risks

| ID | Risk | State |
|---|---|---|
| R1 | A self-fulfilling simulator | **Closed 2026-09-09** — turned into FR-10–FR-14 and NFR-7–NFR-10 in the concept. Asymmetry becomes the x-axis instead of a constant, a symmetric control population gives the series a way to fail, `random`/`oracle` supply the scale, latent structure sits outside the ranker's model class, and the effect threshold is written down before the run |
| R2 | `deck` gains its first database; new startup dependency | **Closed** — `DeckApplicationTests` loads the full context against an unreachable datasource and starts; the provider keeps its last snapshot on a refresh failure |
| R3 | `AgeCompatibilityStrategy` unboxes `candidate.age()` without a null check | **Closed** — fixed in slice 1 with a regression test; the same defect in `LocationProximityStrategy.maxRange` was found and fixed with it |
| R4 | Size: eight pieces after slices 4 and 6 were split | Open — seven done |
| R14 | Time decay on the popularity counter was chosen but never evaluated: a single-pass screen has no time axis | Open — accepted as a design decision without measurement |
| R15 | The gateway guards `/api/v1/admin/**` with `hasRole("ADMIN")`, but the realm has no role named `ADMIN` — only `USER_ADMIN`, matching `USER_BASIC` and `USER_PREMIUM`. Every admin path, including the pre-existing `/api/v1/admin/deck/**`, was unreachable for every user in the realm | **Open — owner decision.** An `ADMIN` role was created and assigned locally so the deployed code works unchanged. The alternative is to change `KeycloakAuthSecurityServiceImpl.isAdmin()` to `USER_ADMIN`, which matches its sibling methods and the realm's own naming — but that changes an authorization rule and is not mine to make |
| R13 | The screen duplicates the ranking formulas from the Java strategies; a change on the Java side would leave it measuring a ranking the service no longer uses | Open — pinned on the Go side only, stated in `deck-sim/README.md` |
| R11 | The driver's issuer can sign a token for any subject. Trusting it outside a disposable bench would be a full authentication bypass | Accepted — owner. Key written `0600`, issuer is a bench-only configuration value, stated in `deck-sim/README.md` |
| R10 | The latent decision weights are chosen, not measured, and are not swept. A steeper popularity skew would make reciprocity matter more | Accepted — owner, recorded in `deck-sim/README.md` and the slice 4a log |
| R12 | The first results interval was a binomial over swipes, which understates uncertainty because swipes by one person are correlated | **Closed 2026-09-10** — replaced with a bootstrap over viewers; two tests pin that fewer people widen the interval at equal swipe counts |


Bugs: none open.

Phase-1 gate approved by the owner on 2026-09-09. `docs/features/swipes-durability-bench/`
was deleted the same day: it rested on a misread of the README durability claim, which
covers the Consumer outbox, not the Kafka ack path.

**This feature is finished as far as it will go.** Slices 1-5 and 6a are built; 6b is
cancelled by the owner and no credits were spent. The platform it produced — named variants,
storage-free assignment, the registry, the admin, the simulator, the outcome screen and the
offline screening pass — is what the next feature runs on.

Next: a new feature for the learned ranker, and the screen has already settled its shape.

- **Popularity first.** `log(1+likes)` × two-directional stated fits, plus a newcomer quota.
  Deck already consumes swipe events (`SwipeEventConsumer`), so the counter needs no new
  event, no new consumer and no call to the consumer service.
- **Embeddings later, on a measured trigger.** The factorisation only overtakes the counter
  at roughly eight likes received per profile, and that figure is a floor because the screen
  hands the model its own generative form. Instrument density; build when it crosses.

Owner decisions already taken: embeddings were chosen as the destination (Q10), popularity
damped by `log(1+likes)` with a newcomer quota (Q11), and time decay on the counter (Q12).
The screen does not contradict the destination — it orders the route.

Time decay cannot be evaluated by a single-pass screen and stays an unmeasured design choice.

Open before a live run:

- `DECK_DB_PASSWORD` in `.env`; on an existing postgres volume `init-databases.sh` does not
  re-run, so `deck_db`, the `deck_app` role and its grants have to be created by hand.
- A Keycloak user with the `ADMIN` realm role, and `ADMIN_APP_ORIGIN` set to the origin the
  admin app is actually served from.
- The bench deployment pointed at the simulator's issuer (`KEYCLOAK_TOKEN_ISSUER` for
  deck-read, `JWK_SET_URL` / `JWT_ISSUER` for swipes-go).
- The generated population seeded into the profiles database under the same ids the
  simulator wrote. This has never been scoped into a slice.
