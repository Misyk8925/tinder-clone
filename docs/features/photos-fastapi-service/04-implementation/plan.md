# Plan: photos-fastapi-service

| # | Slice | Turns green | Mode | Status |
|---|---|---|---|---|
| 1 | FastAPI media pipeline | pytest policy/keys/variants/service/api | AFK | done |
| 2 | Profiles delegates blob work | `UploadPhotoServiceTest` | AFK | done |
| 3 | Match delegates blob work | `ConversationPhotoStorageServiceTest` | AFK | done |

Biggest risk: Match dimension policy tightens from 50–6000 to 300–4096 because the service repeats Profiles photos.

## Phase ledger — combined slices

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P4.1 Slice plan | Done | this file |
| P4.2 Primary evidence | Done | pytest + JUnit listed in `03-behaviour` |
| P4.3 Implementation | Done | `services/photos`, Profiles adapter, Match client |
| P4.4 Unit | Done | pytest + `UploadPhotoServiceTest` + Match mapping test |
| P4.4 Component | Done | FastAPI `TestClient` |
| P4.4 Integration | N/A | live S3/Testcontainers not required for this extraction |
| P4.4 Contract | Done | OpenAPI + API tests |
| P4.4 System/e2e | N/A | full compose stack not started |
| P4.5 Error paths | Done | type/size/corrupt/dimensions/unknown size/empty file |
| P4.6 Review | Done | self-review after writing tests and Java clients |
| P4.7 Targeted defects | Done | root-env-not-loaded fixed; Match policy still RISK-1 |
| P4.8 Quality gates | Done | local pytest 38 / Profiles 37 / Match 3; compose e2e not started |
| P4.9 Handoff | N/A | same context |
| P4.10 Combined-diff review | Done | self-review of FastAPI + two Java consumers |
