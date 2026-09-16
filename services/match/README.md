# Match

Conversations created from confirmed matches. Text chat over REST and WebSocket.

Parent map: [Lunari README](../../README.md) · Domain: chat

## Boundary

**Owns.** Conversation aggregate after `match.created`. `GET /rest/conversations/**`, `GET /ws` / `/ws/**`. Chat photo *API* that calls [photos](../photos/README.md) internally.

**Does not own.** Creating the match ([consumer](../consumer/README.md) outbox). Photo bytes ([photos](../photos/README.md)). Public photo URLs stay on this service’s conversation routes, not on Photos.

**Neighbors.** Kafka `match.created` from consumer. [gateway](../gateway/README.md) upgrades WS and proxies REST. [tinder-client](../../clients/tinder-client/README.md) uses STOMP. Calls [moderation](../moderation/README.md) when `MODERATION_ENABLED=true`.

## Contents

- `MatchService` — materialize conversation from the event.
- Conversation REST + WebSocket chat.
- `PhotosServiceClient` for message images.
- Port `:8080`.

## Decision

**Failure.** Chat load and match persistence fail independently. Folding chat into swipe-write would put WebSocket fanout on the hottest write path.

**Rejected.** Create the conversation inside swipes-go. Inline photo storage.

**Shipped.** Match is a separate consistency domain that starts only from a durable `match.created`.

**Cost.** Chat is empty until the outbox publisher has actually emitted the event.

## Validate

```bash
mvn test
```
