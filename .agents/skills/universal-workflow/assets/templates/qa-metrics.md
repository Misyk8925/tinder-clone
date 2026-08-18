# QA: bio-max-length — `<git sha>`

Env: CI · Evidence: test report link

| Requirement | Check | Status | Evidence |
|---|---|---|---|
| FR-1 / `BIO_TOO_LONG` | `BioLengthTest` | Passed | CI run |

| Suite | Passed | Failed | Skipped | Blocked |
|---|---:|---:|---:|---:|
| Unit | 1 | 0 | 0 | 0 |
| Integration | 0 | 0 | 0 | 0 |

Integration N/A: no IO. Mutation N/A: one comparison, no runner selected.

| NFR | Target | Result | Evidence |
|---|---|---|---|
| NFR-1 | p95 < 50 ms | Blocked | bench env down |

Decision: blocked on NFR-1, not ready to release.
