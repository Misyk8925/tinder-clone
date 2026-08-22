# Bug: GPS-only profile create returns 500

Severity: **blocker** · State: **fixed** · Scope: `POST /api/v1/profiles`

## Expected versus actual

Given a user creates a profile with GPS and no city (the client allows this), the request must persist the profile.

Actual: `500` with `IncorrectResultSizeDataAccessException: Query did not return a unique result: 6 results were returned`.

## Repro

1. Local stack with existing `location` rows whose `city` is `Unknown` (five on this volume; a sixth is inserted during the request).
2. Sign in and create a profile, leaving city blank and allowing GPS.
3. `POST /api/v1/profiles` returns 500.

## Root cause

`CreateProfileService` stores GPS-only profiles as city `Unknown`. The location-go service inserts a new row for every `Unknown` GPS fix (no stable city key). Profiles then mirrors that row and later reconciles `profiles.location_id` with `LocationRepository.findByCity`, which Hibernate treats as a unique result. Duplicate `Unknown` rows make the lookup throw.

`LocationServiceClient` also swallows that persistence exception as “location service unavailable” and inserts yet another local `Unknown` row before `save` fails.

## Regression

`GpsOnlyProfileCreateDuplicateUnknownIT` — Given multiple `Unknown` location rows, When creating a profile with GPS and no city, Then the profile is created and attached to the submitted coordinates. Red on broken code (`500`); green after the location-id save path.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| B.1 Repro | Done | Profiles logs + live `POST /api/v1/profiles` 500 |
| B.2 Expected vs actual | Done | GPS-only create must persist; actual unique-result 500 |
| B.3 Confirmed | Done | Hibernate `findByCity("Unknown")` with 6 rows |
| B.4 Root-cause | Done | Unique city lookup vs per-GPS `Unknown` rows |
| B.5 Contract | Done | HTTP create contract unchanged |
| B.6 Regression red | Done | IT expected 201, got 500 |
| B.7 Smallest safe fix | Done | Persist-by-id; `findFirst` for named-city fallback |
| B.8 Regression green | Done | `GpsOnlyProfileCreateDuplicateUnknownIT` + related tests |
