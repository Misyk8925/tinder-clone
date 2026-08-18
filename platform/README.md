# Platform policy catalogs

The platform directory contains typed desired-state catalogs intended for a future read-only
control plane:

- `kafka-policy-dsl` owns Kafka delivery, retry, DLT, capacity, and client-tuning policy.
- `redis-policy-dsl` owns Redis persistence, eviction, topology, client, namespace, lock, and
  operational-risk policy.

The catalogs are the policy source of truth. AsyncAPI/contracts remain message or key schema
sources; service YAML and Compose remain executable runtime configuration; application code
implements behavior. Catalog tests validate invariants and detect selected runtime drift. Direct
production mutation is intentionally out of scope until a separately reviewed reconciliation
workflow exists.

Validation scripts are deployment guards, not additional authoring sources. A policy change starts
in the relevant Kotlin catalog, then updates the runtime configuration and its drift checks in the
same change.

Run `mvn test` from this directory to validate both catalogs. CI exports JSON catalogs
and fails if Compose declares a Redis store or Kafka topic that is missing from the DSL;
stubs are written to `platform/generated/`.
