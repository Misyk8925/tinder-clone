# Executable Behavioural Contracts: local-media-storage

- **Selected format:** Native Given/When/Then tests in `pytest` (`services/photos/tests/`) and JUnit 5 (`services/gateway/src/test/`, `services/deck-read/src/test/`). No Cucumber added.
- **Canonical test files:**
  - `services/photos/tests/test_local_storage_acceptance.py`
  - `services/gateway/src/test/java/com/tinder/gateway/GatewayRouteConfigurationTest.java`
  - `services/deck-read/src/test/java/com/tinder/deckread/client/PhotoObjectRefTest.java`

## Traceability — FRs & Phase-2 Errors → Tests

| Requirement / Error | Executable acceptance check |
|---|---|
| FR-1 (Pluggable storage abstraction & backend selection) | `test_local_storage_acceptance.py::test_given_local_backend_configured_when_storage_is_built_then_local_storage_is_selected`, `::test_given_memory_backend_configured_when_storage_is_built_then_memory_storage_is_selected`, `::test_given_unknown_backend_configured_when_storage_is_built_then_startup_fails_fast` |
| FR-2 (Atomic local filesystem variant storage) | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds` |
| FR-3 (Local delete & orphan cleanup) | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds`, `::test_given_local_orphans_on_disk_when_cleanup_is_called_then_only_uncatalogued_files_are_removed` |
| FR-4 (HMAC-SHA256 expiring download URLs) | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds` |
| FR-5 (`GET /media/{key}` signed serving) | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds` |
| FR-6 (Gateway `GET /media/**` routing) | `GatewayRouteConfigurationTest::routesSignedLocalMediaGetsToPhotosService` |
| FR-7 (Deck-Read `/media/photos/...` parsing) | `PhotoObjectRefTest::parsesKnownLayouts` |
| FR-8 (Compose volume & env config) | `test_local_storage_acceptance.py::test_given_docker_compose_when_inspected_then_photos_supports_local_storage_volume_and_backend` |
| FR-9 (E2E local photo saving) | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds` (+ single-container Docker smoke check in Phase 4/5) |
| Error: `403 INVALID_SIGNATURE` (missing / tampered / expired) | `test_local_storage_acceptance.py::test_given_invalid_or_expired_signature_when_media_is_requested_then_403_invalid_signature_is_returned` |
| Error: `400 INVALID_IMAGE` / NFR-1 path traversal | `test_local_storage_acceptance.py::test_given_path_traversal_attempt_when_media_is_requested_then_it_is_rejected` |
| Error: `404 PHOTO_NOT_FOUND` | `test_local_storage_acceptance.py::test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds` (step 6 after delete) |
| Error: `503 PHOTO_STORAGE_ERROR` (missing signing secret) | `test_local_storage_acceptance.py::test_given_no_signing_secret_when_local_storage_is_built_then_it_fails_closed` |

## Reverse traceability — Tests → FRs

Every test above maps directly to FR-1..FR-9 or a Phase-2 error row; no orphan tests.

## Unautomatable / runtime-gated behaviour

- Live VPS Dokploy deployment and volume backup rehearsal: out of scope for v1 (recorded as `N/A` / `RISK-3`).
- Single-container Docker smoke check: runs in Phase 4/5 using local Docker daemon (`DOCKER_OK` verified).
