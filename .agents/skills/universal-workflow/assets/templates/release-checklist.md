# Release: bio-max-length — `<git sha>`

P5 sample. One row per P5 ID from `references/phase-ledger.md`. Non-`Done` first.

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P5.6 Monitoring | Not yet observed | window 24h after deploy |
| P5.3 Deploy | N/A | local change, deploy not requested |
| P5.1 Build | Done | `mvn -pl services/profiles test` |
| P5.8 Open risk / blocker | Done | Q1 accepted by product; no blocker bug |

Rollback if runtime ships: revert the length check; old clients keep working.
