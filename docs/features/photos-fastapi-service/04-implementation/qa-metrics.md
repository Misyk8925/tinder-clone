# QA: photos-fastapi-service

Env: local Cloud agent · Evidence: pytest and Maven in this run

| Requirement | Check | Status | Evidence |
|---|---|---|---|
| FR-1–FR-8 | `python3 -m pytest` | Pass | `services/photos` — 38 passed |
| FR-9 | `UploadPhotoServiceTest` + photo policy/keys + props + ArchUnit | Pass | Profiles — 37 passed |
| FR-10 | `ConversationPhotoStorageServiceTest` | Pass | Match — 3 passed |
| NFR-2 | `/health` and `/actuator/health` | Pass | `tests/test_api.py` |

| Suite | Passed | Failed | Skipped | Blocked |
|---|---:|---:|---:|---:|
| Photos pytest | 38 | 0 | 0 | 0 |
| Profiles unit (photos + props + ArchUnit) | 37 | 0 | 0 | 0 |
| Match unit (photo mapping) | 3 | 0 | 0 | 0 |
| Full compose e2e | 0 | 0 | 0 | 1 |

Integration N/A: in-memory object store. Compose e2e Blocked: stack not started (Kafka/Keycloak/Redis/DBs).

Commands:

```
cd services/photos && python3 -m pytest
cd services/tinder-contracts && mvn -B -DskipTests install   # via profiles mvnw
cd services/profiles && ./mvnw -B -ntp test -Dtest=UploadPhotoServiceTest,ConfigurationPropertiesBindingTest,CleanArchitectureTest,PhotoPolicyTest,PhotoKeysTest
cd services/match && ./mvnw -B -ntp test -Dtest=ConversationPhotoStorageServiceTest
```

| NFR | Target | Result | Evidence |
|---|---|---|---|
| NFR-1 | 30 s client timeout | Done | Java client config |
| NFR-2 | `/health` UP | Pass | `test_api.py` |
| NFR-3 | no gateway route | Done | gateway config unchanged |
| NFR-4 | no image bytes in logs | Done | metadata only `x-origin` / `uploaded-at` |
