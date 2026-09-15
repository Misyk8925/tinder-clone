# Tinder Admin — matching observatory

Operator-only local page. It does **not** talk to the gateway, Keycloak, or AWS.

It builds a seeded city in the browser and compares two ranking arms:

- **baseline** — `AgeCompatibilityStrategy` (1.0) + `LocationProximityStrategy` (0.8), the same sum `ScoringService` uses in `services/deck`
- **popularity** — that score × `log(1+likes)`, with 12% of deck slots interleaved for low-impression newcomers

## Run

```bash
npm ci
npm test
npm run dev
```

Play starts a night of live cards. Match overlay is why AWS would matter: same UI, real photos and Kafka decks.
