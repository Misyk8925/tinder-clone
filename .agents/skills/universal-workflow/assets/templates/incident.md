# Incident: bio validation always 500

Feature: `bio-max-length` · sha: `abc123` · SEV3
Detected: 2026-08-18 15:10 alert · Resolved: 15:25 rollback

Users could not save any bio. Rollback restored writes.

Cause: off-by-one shipped without the 500-char case. Guardrail miss: phase 3 had only the 501 path.

| Action | Phase | Owner |
|---|---|---|
| `BioLengthTest.acceptsBioOf500` red then green | 3/4 | profiles |
| Alert on bio 4xx spike | 5 | profiles |

Closed when the action is merged, not when service returns.
