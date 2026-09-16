# Contracts — Deck semantic shadow

| Boundary | Canonical | Readable view | Impact |
|---|---|---|---|
| OpenSearch index | [`deck-semantic-shadow-index.json`](../../../contracts/data/deck-semantic-shadow-index.json) | This document | New, disposable synthetic index |
| Report file | [`deck-semantic-shadow-report.schema.json`](../../../contracts/data/deck-semantic-shadow-report.schema.json) | This document | New bench-only JSON artifact |
| Profile update/delete events | [`deck-semantic-shadow.asyncapi.yaml`](../../../contracts/events/deck-semantic-shadow.asyncapi.yaml) | [`deck-semantic-shadow.asyncapi.md`](../../../contracts/events/deck-semantic-shadow.asyncapi.md) | Existing topics and payloads only |
| Public HTTP, websocket, relational schema | None | None | Unchanged |

## Index and query rules

One document per profile ID, with two 512-dimension normalized vectors, synthetic eligibility
fields and source/model/text versions. No raw bio or prompt text is stored. The profile source
revision is monotonic; upsert requires a newer revision and delete leaves a source tombstone.
Rebuild uses the complete synthetic corpus, not Kafka retention. An index document with a
missing or mismatched source revision is ineligible for the final shadow Top-20.

Two ANN searches use `k=200` each: viewer preference against candidate identity, and viewer
identity against candidate preference. The union is deduplicated by profile ID. A broad
source-corpus sample measures missed eligible neighbours. The fixed hypothesis score is
`0.6*forward + 0.4*reverse`; both similarities must be present. The baseline ranker scores
the union before the shadow Top-20 is selected. The final eligibility check reads the current
synthetic source and excludes self, deleted/inactive/invisible/moderation-rejected, age/gender/
radius mismatches in both directions, blocked/reported and previously swiped profiles. The
index's approximate prefilter is an optimization, not an authority.

## Report semantics

The report is a local, bench-only JSON file. `baselinePosition` and `baselineScore` are null
for profiles absent from baseline. `positionDelta` is null unless both positions exist. All
positions are one-based. Comparison on a common union is calculated separately from the
new-candidate count. `retrievalLatencyMs` is the full two-query retrieval time. Cold start is
reported outside warm p50/p95. Null latency or cost means unobserved, never zero.

The report includes only IDs, scores, version strings, counts and matched hobby names. No
raw bio, vectors, AWS credentials or access tokens. It is not a client response or admin API.
The synthetic report is deleted with the disposable stand after its summary is saved.

## Failure and compatibility

The shadow path has a two-second warm search timeout, bounded concurrency, and no awaited
dependency on baseline publication. Missing embeddings, source record, version mismatch,
provider failure or timeout produces a typed fallback. No fallback writes to Deck Redis or
publishes `deck.built.v1`. Old clients, Deck Read and existing event consumers see no change.
The AWS budget alarm is advisory because billing is delayed; the runner also caps volume and
duration and always requests cleanup in a finally path.
