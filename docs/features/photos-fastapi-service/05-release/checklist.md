# Release: photos-fastapi-service

Deploy was not requested.

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P5.1 Build / validation | Pending | pytest + Profiles/Match tests in this run; CI on the PR |
| P5.2 Security scan | Blocked | GitHub Security workflow on the PR |
| P5.3 Deploy | N/A | not requested |
| P5.4 Smoke | N/A | no deploy |
| P5.5 Rollback | Done | revert this branch; Profiles/Match again would need the previous S3 adapters, so roll back all three together |
| P5.6 Monitoring | Not yet observed | no deploy |
| P5.7 Docs | Done | README, AGENTS.md, match ARCHITECTURE, OpenAPI |
| P5.8 Open risk | Done | RISK-1 accepted; RISK-2 mitigated; no blocker bug |

Rollback: revert the PR as a unit. Do not leave Profiles/Match pointing at a removed photos service.
