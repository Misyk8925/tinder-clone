# location-go

Standalone location service: city entity, Nominatim geocoding, PostGIS geography.

Parent map: [Lunari README](../../README.md) · Domain: profile

## Boundary

**Owns.** The `location` table, `POST /api/v1/locations/resolve`, `GET /api/v1/locations/{id}`, L1 `sync.Map` plus DB plus `singleflight` per city, circuit-breaker around Nominatim.

**Does not own.** The profile aggregate ([profiles](../profiles/README.md)). Haversine scoring on the deck hot path ([deck](../deck/README.md) still computes distance locally). Public client APIs — Profiles calls this service.

**Neighbors.** [profiles](../profiles/README.md) is the caller. Architecture notes: [docs/architecture.md](docs/architecture.md).

## Contents

Single binary under `cmd/location/`:

- `handler.go` — HTTP
- `service.go` — cache / singleflight / resolve-from-coords
- `repository.go` — PostGIS
- `geocoder.go` — Nominatim + `sony/gobreaker`; open circuit falls back to default coordinates

Port `:8065`. Health: `GET /health`.

## Decision

**Failure.** Location logic was duplicated: Profiles owned the entity + geocoding; Deck reimplemented proximity. A Nominatim blip sat on profile writes without isolation.

**Rejected.** A geocoding proxy that still left the table in Profiles. Keep everything in Profiles.

**Shipped.** Full standalone service (entity + geocode + PostGIS), as recorded in [docs/architecture/service-refactor-plan.md](../../docs/architecture/service-refactor-plan.md).

**Cost.** An extra hop on profile create/update. Deck still does local Haversine so scoring does not wait on this service.

## Validate

```bash
go test ./...
```
