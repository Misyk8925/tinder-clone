# Executable behaviour — Deck semantic shadow

Project convention: native JUnit and Go tests with Given/When/Then names and setup. No
Cucumber dependency. The first red test is already in the Go simulator. The remaining
acceptance tests are specified below and must be added before the combined phase 2/3 gate;
this document does not claim they exist yet.

| Requirement | Acceptance check | Status |
|---|---|---|
| FR-1 | `TestSemanticPopulationCarriesBioAndHobbies`; fixed seed produces identical corpus and truth | First test executable and red; determinism test pending |
| FR-2 | Given an updated/deleted/replayed synthetic profile, index revision advances/deletes/no-ops; full rebuild matches source | Pending Go indexer acceptance tests |
| FR-3 | Given shadow runner failure, Deck still completes and publishes its baseline snapshot | `SemanticShadowDeckAcceptanceTest`, executable source, red at compilation because `ShadowRunner` is absent; Deck Read parity check pending |
| FR-4 | Given opposite-direction neighbours, two ANN pools are unioned and both similarities use 0.6/0.4 | Pending JUnit semantic engine test |
| FR-5 | Given stale, blocked, reported or otherwise ineligible index hits, final shadow Top-20 contains none | Pending JUnit source eligibility test |
| FR-6 | Given common and new candidates, baseline position/delta are only computed for common candidates; current ranker orders shadow pool | Pending JUnit comparator/report test |
| FR-7 | Given shared hobbies, explanation lists only those; given none, it is absent | Pending JUnit explanation test |
| FR-8 | Given max volume/duration or cleanup failure, runner stops, records status and attempts cleanup | Pending Go runner test with fake cloud adapter |

| Error or edge path | Acceptance check | Status |
|---|---|---|
| Bedrock/OpenSearch timeout or 5xx | Baseline completes, typed fallback is counted; no shadow write | Pending JUnit fake-provider test |
| Missing embedding or source record | Candidate is excluded, `fallback_missing_data` recorded if viewer cannot be compared | Pending JUnit test |
| Old model/text version or source revision | Candidate is excluded; index refresh is requested | Pending JUnit test |
| Duplicate or out-of-order event | Latest source revision wins; delete tombstone prevents resurrection | Pending Go test |
| Empty hobbies | No preferenceVector and no inferred preference or explanation | Pending Go and JUnit tests |
| Budget/time limit | Runner stops without further model calls and records incomplete metrics as null | Pending Go fake-adapter test |

The red first-slice command is:

```sh
cd services/deck/load-tests/deck-sim
GOCACHE=/private/tmp/deck-semantic-go-cache go test ./... -run TestSemanticPopulationCarriesBioAndHobbies -count=1
```

Observed result on 2026-09-16: test failed because the first generated profile lacks bio and
hobbies. No AWS service was called. Until the pending acceptance checks are executable, the
combined phase 2/3 gate remains blocked and phase 4 must not begin under universal-workflow.

Deck test compilation was run offline with `./mvnw -B -o -DskipTests test-compile` from
`services/deck`. Production source compiled; test compilation failed at the intended missing
`com.tinder.deck.semantic.ShadowRunner` seam. `tinder-contracts` package compiled, but its
install to `~/.m2` was denied by the filesystem sandbox; Deck resolved the existing local jar.
