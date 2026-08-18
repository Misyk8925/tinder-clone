# Plan: bio-max-length

| # | Slice | Turns green | Mode | Status |
|---|---|---|---|---|
| 1 | Reject bios > 500 chars | `BioLengthTest.rejectsBioLongerThan500` | HITL | todo |

Slice 1: `POST` 501 chars → 400 `BIO_TOO_LONG`. Files: existing profile write path + `BioLengthTest`. Tests: unit Done; IO levels N/A. Biggest risk: clients already sending long bios.

## Phase ledger — slice 1

Sample rows only. One row per active P4 ID from `references/phase-ledger.md`.

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P4.2 Primary evidence/red | Done | `BioLengthTest` red without the check |
| P4.4 Integration | N/A | no IO boundary |
| P4.6 Review | Blocked | no second agent; self-review next |
