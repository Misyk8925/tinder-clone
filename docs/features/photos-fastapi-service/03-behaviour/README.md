# Behaviour — photos-fastapi-service

Format: native Given/When/Then · `python -m pytest` in `services/photos` · `./mvnw -B -ntp test` in Profiles and Match

| FR or error | Test | File |
|---|---|---|
| FR-1 unsupported type | rejects other types / API 400 | `tests/test_policy.py`, `tests/test_api.py` |
| FR-2 oversize | rejects anything larger | `tests/test_policy.py` |
| FR-3 corrupted | undecodable bytes | `tests/test_service.py` |
| FR-4 dimensions | too small / too large | `tests/test_policy.py`, `tests/test_service.py` |
| FR-5 four JPEG variants | stores four keys and URLs | `tests/test_service.py`, `tests/test_api.py` |
| FR-6 delete | objects gone after DELETE | `tests/test_api.py` |
| FR-7 download URL | signed URL contains size | `tests/test_api.py` |
| FR-8 cleanup | only uncatalogued ids removed | `tests/test_service.py`, `tests/test_api.py` |
| FR-9 Profiles slots + catalogue | `UploadPhotoServiceTest` | Profiles |
| FR-10 Match mapping | `ConversationPhotoStorageServiceTest` | Match |
| 400 `INVALID_IMAGE` unknown size | download-url gigantic | `tests/test_api.py` |
| 400 empty file | Photo file is required | `tests/test_api.py` |
| Root `.env` / `.env.local` | cwd is the service dir, bucket comes from repo root | `tests/test_config.py` |
