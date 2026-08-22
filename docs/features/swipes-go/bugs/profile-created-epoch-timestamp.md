# Swipes Go rejects `profile.created` because Jackson writes Instant as a number

## Status

Fixed 2026-08-22.

Severity: major · Scope: `services/swipes-go` Kafka consumers for `profile.created` and `profile.deleted`

## Expected versus actual

Given Profiles publishes `profile.created` with Jackson `Instant` `timestamp`, Swipes must upsert `profile_cache` for JWT-sub ownership and existence checks.

Actual: Go `encoding/json` failed with `Time.UnmarshalJSON: input is not a JSON string`. The consumer retried six times and did not apply the event.

## Repro

Live local Kafka `profile.created` partition 0 offset 292:

```json
{"eventId":"be0062e6-2adf-4777-978d-a24c664505c5","profileId":"7e7dac54-26c8-4e51-ab68-2708ea4a278b","userId":"972bb558-f7f4-4404-8ffc-368162014e18","timestamp":1787342115.082518133}
```

`swipes` logs: `Kafka consumer rejected event topic=profile.created offset=292 ... Time.UnmarshalJSON: input is not a JSON string`.

## Root cause

Spring Kafka `JsonSerializer` uses its own ObjectMapper and keeps `WRITE_DATES_AS_TIMESTAMPS` on. `Instant` is therefore a JSON number (epoch seconds plus nano fraction), not RFC3339. Go `time.Time` only unmarshals JSON strings. Java `swipes-demo` accepted the number via Jackson `Instant`.

## Fix and regression evidence

`EventTimestamp` accepts Jackson epoch seconds (fractional), epoch millis, RFC3339, and null. Used on create and delete events.

`TestGivenJacksonEpochSecondsWhenProfileCreatedIsUnmarshalledThenTimestampIsParsed` — red on `*time.Time`, green after `EventTimestamp`.

Live after rebuild: `swipes` healthy; `profile.created` partition 6 committed through offset 293 (the previous poison offset) with no `UnmarshalJSON` errors.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1–R.3 | Done | bug-fix; P1–P3 and P4.10 Mode-omit |
| B.1–B.4 | Done | Live payload `timestamp: 1787342115.082518133`; Jackson Instant number vs `time.Time` |
| B.5 | Done | Event JSON fields unchanged; Go accepts the existing wire format |
| B.6–B.8 | Done | Red then green model unmarshal tests |
| P4.4 | Done | Go unit; Kafka/IT N/A — payload captured from live topic |
| P4.5 | Done | null timestamp still unmarshals; RFC3339 still accepted |
| P5.3–P5.4 | Done | Local `swipes` image rebuilt; consumer committed past offset 292 |
| P5.6 | Not yet observed | Authenticated new-profile create is user-owned smoke |
