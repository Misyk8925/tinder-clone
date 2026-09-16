# Deck semantic shadow event consumer

The synthetic indexer consumes existing `profile.updated` and `profile.deleted` topics.
Profiles remains the producer. Both topics use `profileId` as the Kafka key; no event payload,
topic, partition count, or existing consumer is changed.

| Topic | Payload | Action | Duplicate / old delivery |
|---|---|---|---|
| `profile.updated` | `eventId`, `profileId`, `changeType`, `changedFields`, `timestamp`, optional `metadata` | Read the newest full synthetic profile, then regenerate both vectors if its source revision exceeds the indexed revision | Ignore older or repeated revisions |
| `profile.deleted` | `eventId`, `profileId`, `timestamp` | Tombstone the synthetic source and remove its index document | Repeated removal succeeds as a no-op |

The source corpus is authoritative for reindex. Kafka retention is the deployment's existing
retention and is not relied on as the rebuild archive. Failed indexing is retried with a bounded
backoff, then recorded in the run report; it cannot fail Deck's baseline consumer. No bio, vector,
token or raw event metadata is logged. The payload shape is the existing Java contract in
`services/tinder-contracts`.
