# Gates: profiles

Baseline: `main` · CI: workflow link

| Gate | Command | Policy | Latest |
|---|---|---|---|
| HTTP compatibility | `spectral lint` + breaking-change job | no unapproved break | Passed |
| Migration safety | N/A | no schema change | N/A |
| Perf regression | `k6` bio write | NFR-1 p95 < 50 ms | Blocked — bench env down |

Blocked is not a pass. Exceptions need owner, expiry, and a risk issue.
