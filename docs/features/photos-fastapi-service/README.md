# Photos FastAPI service

Repo-local tracker: [`00-state.md`](00-state.md). Linear is not authorized in this environment.

Indexes canonical artifacts. Does not copy specs or tests.

| Folder | What to open |
|---|---|
| `01-concept.ru.md` | Problem, FRs/NFRs, cheapest design |
| `02-contracts/` | Links to the HTTP spec |
| `03-behaviour/` | FR → pytest / JUnit |
| `04-implementation/` | Slice plan and log |
| `05-release/` | Build/validation only; deploy not requested |

## Boundary that must not drift

Client-facing photo URLs stay on Profiles (`/api/v1/profiles/photos/**`) and Match (`/rest/conversations/{id}/messages/photos`). The FastAPI service is internal. Photo catalogue rows, slot policy and deck-card events stay in Profiles.

## Phase ledger — implementation

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | full-feature-delivery: new internal service and two consumers |
| R.2 Project profile | Done | repo-local tracker; English contracts; native tests; OpenAPI 3.1 under `docs/contracts/http` |
| P2.1 HTTP | Done | `docs/contracts/http/photos.openapi.yaml` |
| P2.2 Events | N/A | no new events; Profiles still publishes card changes after catalogue writes |
| P2.3 Websocket | N/A | no websocket |
| P2.4 Data | N/A | no schema change; catalogue stays in Profiles |
| P4.3 Implementation | Done | `services/photos`, Profiles `PhotoMediaPort`, Match `PhotosServiceClient` |
| P5.3 Deploy | N/A | deploy not requested |
