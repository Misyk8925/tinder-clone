# Bug: 500-character bio is rejected

Severity: minor · State: Confirmed · Scope: `POST /profiles/{id}`

Repro: save bio of exactly 500 characters.
Expected: save. Actual: `BIO_TOO_LONG`.

Cause: `length >= 500` instead of `length > 500`. Diagnosis in progress is `unknown — diagnosis in progress`.

## Phase ledger

Sample rows. Copy B.1–B.8 plus applicable P4/P5 from `references/phase-ledger.md`.

| Sub-step | Status | Evidence or reason |
|---|---|---|
| B.1 Repro | Done | 500-char fixture |
| B.6 Regression red | Blocked | test not written yet |
| P5.3 Deploy | Mode-omit | unreleased local fix |

Regression: `BioLengthTest.acceptsBioOf500` — must fail on broken code, pass on fix. Race: N/A.
