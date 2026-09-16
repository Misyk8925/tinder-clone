# deck-sim — simulated population for the ranking experiment

Parent map: [deck](../../README.md) · [Lunari README](../../../../README.md)

**Owns.** Offline population, screen, and drive against a disposable bench. Latent
preferences stay in a separate file from platform-visible profiles.

**Does not own.** Production ranking or auth. The signing key is bench-only (FR-11).
Java scoring stays in [deck](../../README.md); this screen pins a Go copy and can drift
(R13 in the experiments log).

Generates a reproducible population of agents with latent preferences, for the
`baseline` vs `reciprocal` experiment owned by
[`docs/features/deck-ranking-experiments`](../../../../docs/features/deck-ranking-experiments).

Three commands. `generate` builds the population, `screen` answers offline whether a paid
run is worth making, and `drive` walks viewers through their decks against a real stack.

```bash
go test ./...

# 1. population
go run . generate -size 100000 -asymmetry 0.5 -seed 1 -out ./out

# 2. is there anything to find? — offline, no stack, no money
go run . screen -size 100000 -viewers 2000

# 3. the JWKS the bench services must verify against
go run . jwks -key ./out/signing-key.pem > ./out/jwks.json

# 4. the run
go run . drive -profiles ./out/profiles.jsonl -latent ./out/latent.jsonl \
  -key ./out/signing-key.pem -base-url http://gateway:8222 \
  -viewers 2000 -concurrency 32 -out ./out/outcomes.jsonl
```

Writes two files, deliberately separate:

- `profiles.jsonl` — everything the platform may know: age, location, stated window and radius.
- `latent.jsonl` — taste vector, appeal, selectivity, patience.

Outcome metrics may read only the first. Separate files make a leak visible instead of
accidental (FR-14). The bench-only `oracle` variant is the single exception, and that is
why it never exists in a production profile.

## The offline screen

`screen` estimates what each ranking variant would produce, without any running service. For
each viewer it takes the candidates actually near them — capped at 2000, mirroring deck's
own `search-limit` — orders them under a variant, and sums the true mutual-like probability
of the cards the viewer's patience lets them reach. Ranking changes only *which* cards land
in reach, which is exactly what this compares.

It is a screening step, not the experiment. It cannot exercise the deck service, the read
model or the event path. What it can answer is whether there is room between today's ranking
and the best any ranking could do — a property of the population and the ordering formulas
rather than of a deployment. If there is no room, the paid run has nothing to find.

Six arms. `random` is the floor and `oracle` the ceiling, so the others have a scale;
`oracle` reads latent state and exists only here. `baseline` is today's ranking,
`reciprocal` adds the mirrored preference terms, `learned` ranks by observed popularity
damped with `log(1+likes)`, and `embedding` factorises the observed swipe matrix. The two
learned arms train on a **baseline pass**, which is what a real training set is: a profile
today's ranking never surfaced carries no evidence, and both reserve a slice of every deck
for profiles the model has never seen, or a new profile would never be shown and so never
acquire the evidence that would let it be shown.

### What the screen found

Absolute levels are optimistic — see the circularity note below — but the comparison between
arms under identical data is the useful part.

| observations per profile | learned (popularity) | embedding (factorisation) |
|---|---|---|
| 5.3 | 34% of ceiling | 32% |
| 8.1 | 34% | 35% |
| 12.6 | 38% | 40% |
| 25.2 | 35% | 41% |

**The binding constraint is data density, not model class.** A factorisation fitted on five
observations per profile is fitting noise and loses to a popularity scalar. It overtakes
around eight observations per profile and pulls away above that.

Since the embedding model shares its functional form with the simulator's own decision rule,
it is *favoured* here — so eight is a floor on what a real system would need, not an
estimate of it.

**Circularity, stated plainly.** The simulator scores taste as a dot product of latent
vectors, and a matrix factorisation estimates dot products of latent vectors. The embedding
arm is therefore handed its own generative form, which no real model ever gets. Read its
absolute number as an upper bound on the approach. The popularity arm does not have this
problem: the generator has appeal as a scalar and the ranker has to recover it from
exposure-biased counts, which is a real estimation task.

**The ranking formulas are duplicated from the Java strategies.** That is the price of
screening without a platform, and it is a real hazard: if either copy changes, this measures
a ranking the service no longer uses. `TestRankingParityWithTheJavaStrategies` pins the Go
side; nothing pins the Java side.

## Tokens without Keycloak users

`drive` signs its own RS256 tokens, one per viewer, and publishes the matching JWKS. Both
services that resolve a viewer validate against configurable settings — deck-read's
`quarkus.oidc.token.issuer`, and `JWK_SET_URL` / `JWT_ISSUER` / `JWT_AUDIENCE` in
swipes-go — so pointing a bench deployment at this issuer is configuration, not code. A
hundred thousand viewers never become a hundred thousand Keycloak users.

This belongs to a disposable bench and nowhere else. Whoever holds `signing-key.pem` can
sign a token for any subject, which is why the file is written `0600` and why the issuer
must never be trusted by a real environment.

**Configure services from `deck-sim jwks`, not from the server built into `drive`.** That
server lives only as long as the run: it is a convenience for watching a live run, and it
goes away when the command exits. A static `jwks.json` is what a service should be pointed
at.

## Why the population is shaped this way

**Asymmetry is a parameter, not a constant.** A match needs both sides, so one-directional
ranking losing is nearly a tautology. Its *magnitude* is not: it is set entirely by how much
viewers' stated windows disagree. Sweeping `-asymmetry` makes that the x-axis of the result
instead of a hidden assumption.

**`-asymmetry 0` is the control population.** Every agent gets the same window width around
their own age and the same radius, so acceptance is exactly symmetric: A wants B if and
only if B wants A. Measured one-sided share is 0.0000 there. If `reciprocal` ever shows a
gain on this population, the harness is broken — that is the point of having it.

An identical *absolute* window would not have been symmetric: if everyone wanted 25..35,
acceptance would depend only on the candidate's age.

**Patience is what makes ranking matter at all.** Each agent goes through a bounded number
of cards. Without that, every viewer would eventually see every candidate and the order
would be irrelevant.

**The decision sits outside the ranker's model class.** `baseline` sums an age curve and a
distance curve; `reciprocal` adds their mirrors. Neither can express a latent taste vector,
a heavy-tailed appeal or a per-agent selectivity, so no variant recovers this function and
the comparison is a contest rather than a restatement of the generator.

**Stated preferences bend behaviour, they do not gate it.** Being outside a window is an
exponential penalty, not a filter. A hard cut would make stated preferences perfectly
predictive and hand the heuristics an advantage reality does not give them.

## Measured behaviour

100,000 profiles, seed 1, generated in about 0.4 s:

| asymmetry | one-sided pairs | mutual pairs | interest, top decile |
|---|---|---|---|
| 0 | 0.0000 | 0.0282 | 0.3460 |
| 0.25 | 0.0207 | 0.0272 | 0.3411 |
| 0.5 | 0.0351 | 0.0258 | 0.3359 |
| 0.75 | 0.0449 | 0.0239 | 0.3294 |
| 1 | 0.0507 | 0.0229 | 0.3216 |

One-sided acceptance rises monotonically from exactly zero, and mutual acceptance falls as
it does. Interest concentration stays near a third across the whole sweep, so the two knobs
are not confounded: moving asymmetry does not quietly move the popularity skew with it.

## Reading the numbers a run prints

`shown`, `liked` and `swipes` describe the deck the platform actually served. A like rate
far below the roughly 20% this model produces for nearby, in-window candidates usually means
the deck was serving distant ones, not that the agents are picky — distance is the strongest
term in the decision, so an unranked or unbuilt deck collapses the like rate.

`outcomes.jsonl` holds counts only: viewer id, shown, liked, passed, unknown, generation and
deck state. No latent value appears in it, so a metric built on that file cannot read the
generator's ground truth. `unknown` counts cards outside the generated population — they are
counted and skipped rather than decided, because inventing a preference for them would bias
the outcome.

## Known residual assumption

The weights of the latent decision in `decide.go` are constants chosen so the most wanted
tenth receives roughly 35% of all like probability. That level is a judgement, not a
measurement, and it is not swept: a steeper skew would make reciprocity matter more, a
flatter one less. Calibrating it against external dating-population data would remove the
last arbitrary choice here.
