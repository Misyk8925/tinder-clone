# Release checklist — complete-moderation-service

Production deployment is outside the authorized task. This is a release handoff, not a
claim that the service was deployed.

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P5.1 Build | Done | JDK 24: `./gradlew clean test acceptanceTest bootJar`. |
| P5.2 Security scan | Blocked | Focused secret/security tests passed; no SCA/SAST/container scanner is installed in this VM. |
| P5.3 Deploy | N/A | Production deployment was not requested. |
| P5.4 Smoke | Partial | Keyless local runtime and browser smoke passed; live PostgreSQL/Kafka/providers require deployment configuration. |
| P5.5 Rollback | Done | Revert the feature commits; V1–V3 are additive and must not be destructively rolled back after data is written. |
| P5.6 Monitoring | Not yet observed | No deployment or observation window. |
| P5.7 Docs/runbook | Done | Contracts, implementation plan/log, QA metrics, health/metrics and this handoff are current. |
| P5.8 Open risk / blocker | Blocked | PostgreSQL-backed NFR-1 and Kafka integration are unavailable; retention approval, provider account/model, Kafka names/ACLs, scanners and live infrastructure smoke remain. |

Release only after:

1. approve the raw/evidence retention periods;
2. configure BCrypt users and provider credentials without committing them;
3. configure PostgreSQL and Kafka topics/ACLs and run migration, retry/DLQ and restart checks;
4. run SCA/SAST/secret and container-image scans with no unresolved high/critical finding;
5. deploy progressively, run health/keyless-or-live-provider smoke, and observe latency/error
   metrics before marking P5.6 and P5.8 complete.
