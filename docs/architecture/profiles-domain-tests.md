# Profiles — Domain Layer Test Suite

> Companion to [`service-refactor-plan.md`](./service-refactor-plan.md) §10 (Testing strategy).
> Documents the unit tests for the pure domain layer `com.tinder.profiles.domain.profile`.

## Scope & philosophy

These tests cover the **pure domain layer only** — the aggregate, its value objects
and the domain service. Because that layer has **no framework dependencies**
(no Spring, JPA, Redis, Kafka, WebClient, Lombok), the tests are:

- **Plain JUnit 5 + AssertJ** — no `@SpringBootTest`, no application context, no
  Testcontainers, no Mockito. Nothing to wire up.
- **Fast** — the full suite (38 tests) runs in well under a second.
- **Deterministic** — no I/O, no clock dependence except where a domain fact
  legitimately uses "now" (`markAsDeleted`), which is only asserted as non-null.

This is the payoff of the hexagonal split: logic that previously required
constructing `ProfileApplicationService` with ~15 mocks just to exercise a
formula is now testable in isolation.

## Conventions (BDD)

Every test class follows the same Behaviour-Driven style:

- `@DisplayName` on the class names the unit under test.
- `@Nested` inner classes group **scenarios** ("when constructed", "when
  soft-deleted", "when detecting changes", …).
- `@DisplayName` on each method reads as a behavioural assertion
  ("a deleted profile cannot be reactivated").
- Bodies follow **given / when / then**; AssertJ's BDD entry points
  (`then(...)`, `thenThrownBy(...)`, `thenNoException()`) make the assertion
  phase read as "then …".

Location: `services/profiles/src/test/java/com/tinder/profiles/domain/profile/`

## Coverage catalogue

### `MatchingPreferencesTest` — value object (7)
Self-validating matching criteria.

| Scenario | Behaviour asserted |
|---|---|
| when constructed | rejects `minAge > maxAge` (`DomainValidationException`) |
| | accepts equal min/max age |
| | accepts a null age bound (nothing to validate) |
| comparing criteria | `sameCriteriaAs` true for identical criteria |
| | false when any of minAge/maxAge/gender/maxRange differs |
| | false when compared against `null` |
| | matching null fields are treated as equal |

### `ProfileChangeSetTest` — value object (7)
Change accumulation and significance classification.

| Scenario | Behaviour asserted |
|---|---|
| no changes | empty set → `NON_CRITICAL` |
| classify priority | a `city` change wins over everything → `LOCATION_CHANGE` |
| | preferences win over critical fields (no city) → `PREFERENCES` |
| | `age`/`gender` alone → `CRITICAL_FIELDS` |
| | `name`/`bio` alone → `NON_CRITICAL` |
| preferences marked | flags the change, exposes `"preferences"`, no longer empty |
| changed-fields view | returned set is unmodifiable |

### `ProfileTest` — aggregate root (16)
State transitions and invariants.

| Scenario | Behaviour asserted |
|---|---|
| soft delete | sets deleted, deactivates, timestamps `deletedAt` |
| | a deleted profile is not deletable; a live one is |
| (de)activate | **a deleted profile cannot be reactivated** (key invariant) |
| | a live profile can be reactivated; deactivate clears the flag |
| premium | activating stores expiry; revoking clears it |
| movement | first coordinate update accepted when no position known |
| | detects a move beyond threshold; ignores sub-threshold jitter; threshold counts as significant |
| relocate | updates position + city; keeps existing city when new city is blank |
| hobbies | copied defensively on replace; exposed as an unmodifiable view |

### `ProfileDomainServiceTest` — domain service (8)
Cross-cutting validation and change detection.

| Scenario | Behaviour asserted |
|---|---|
| require location | rejects an edit with neither city nor coordinates |
| | accepts city-only; accepts coordinates-only |
| detect changes | identical edit → no changes |
| | fields absent from a partial edit are ignored (patch semantics) |
| | differing basic fields are all reported |
| | preferences flagged only when the criteria differ |
| | provided hobbies count as a change |

## Intentionally **not** covered here

- **`GeoPoint` has no dedicated test class** — it is exercised indirectly via
  `Profile.hasMovedBeyond`/`relocate`, and is the component earmarked for
  extraction into the shared `tinder-geo` library (plan §5.4), where its own
  Haversine tests will live.
- **Persistence, serialization, caching, Kafka** — these belong to
  infrastructure/application integration tests (Testcontainers / EmbeddedKafka),
  not the domain suite.

## Running

```bash
cd services/profiles

# the whole domain suite
mvn -o test -Dtest='ProfileTest,ProfileDomainServiceTest,ProfileChangeSetTest,MatchingPreferencesTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

# a single class / nested scenario
mvn -o test -Dtest='ProfileTest$Movement'
```

## Maintenance note

The `ProfileDomainServiceTest` cases for `detectChanges` assume `ProfileEdit`
(the edit command) currently lives in the domain. There is an open decision to
relocate `ProfileEdit` to the application layer; if/when that lands, those
specific cases move with it. The other three test classes are unaffected.
