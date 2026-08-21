# QA: photos-fastapi-service

Env: local Cloud agent · Evidence: pytest and Maven in this run

| Requirement | Check | Status | Evidence |
|---|---|---|---|
| FR-1–FR-8 | `python -m pytest` | Pending this run | `services/photos` |
| FR-9 | `UploadPhotoServiceTest` | Pending this run | Profiles |
| FR-10 | `ConversationPhotoStorageServiceTest` | Pending this run | Match |

| Suite | Passed | Failed | Skipped | Blocked |
|---|---:|---:|---:|---:|
| Photos pytest | | | 0 | 0 |
| Profiles unit (photos + props) | | | 0 | 0 |
| Match unit (photo mapping) | | | 0 | 0 |
| Full compose e2e | 0 | 0 | 0 | 1 |

Integration N/A: in-memory object store. Compose e2e Blocked: stack not started.

| NFR | Target | Result | Evidence |
|---|---|---|---|
| NFR-1 | 30 s client timeout | Done | Java client config |
| NFR-2 | `/health` UP | Pending pytest | `test_api.py` |
| NFR-3 | no gateway route | Done | gateway config unchanged |
| NFR-4 | no image bytes in logs | Done | metadata only `x-origin` / `uploaded-at` |
