# State: photos-fastapi-service

Fallback tracker. Linear MCP is unauthorized. Current phase: **4 — implementation**.

| Phase | Artifact | Approved by | Date |
|---|---|---|---|
| 1 Concept | request + `01-concept.ru.md` | user request (FastAPI, replicate Profiles photos, integrate Profiles and Match) | 2026-08-21 |
| 2+3 Contracts + behaviour | OpenAPI + pytest/JUnit | implemented with the request; no separate gate in this Cloud run | 2026-08-21 |
| 4 Implementation | `services/photos` + Java clients | this change | 2026-08-21 |

## Decisions

| Date | Decision | Source |
|---|---|---|
| 2026-08-21 | FastAPI, not Java/Go | user request |
| 2026-08-21 | Replicate Profiles photos processing (type/size/dimensions, four JPEG variants, S3 keys, presign, orphan cleanup) | user request |
| 2026-08-21 | Integrate into Profiles and Match | user request |
| 2026-08-21 | Catalogue, slots, JWT and deck events stay in Profiles | existing `PhotoCatalogPort` / `UploadPhotoService` |
| 2026-08-21 | Internal HTTP, no gateway, no JWT (same pattern as location-go) | cheapest analogue |
| 2026-08-21 | No local S3 fallback in Profiles/Match | extraction would be pointless if both copies remain |
| 2026-08-21 | Photos loads repo-root `.env` / `.env.local`, not cwd `.env` | user request |

## Risks

| ID | Status | Risk | Mitigation |
|---|---|---|---|
| RISK-1 | Accepted | Chat photos that were 50–299 px or 4097–6000 px now fail | same policy as Profiles; caller sees `INVALID_IMAGE` |
| RISK-2 | Mitigated | Photos down fails uploads | `PHOTO_STORAGE_ERROR` / 503; no silent local fallback |

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P1.12 Gate | Done | treated the explicit service request as concept approval for this Cloud delivery |
| P3.7 Combined package | Done | OpenAPI + pytest/JUnit in the same change |
| P5.3 Deploy | N/A | not requested |
