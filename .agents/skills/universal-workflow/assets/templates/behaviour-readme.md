# Behaviour — bio-max-length

Format: native Given/When/Then · `mvn -pl services/profiles test -Dtest=BioLengthTest` · status: red for missing check

| FR or error | Test | File |
|---|---|---|
| FR-1 | rejects bio longer than 500 | `BioLengthTest` |
| 400 `BIO_TOO_LONG` | same test | `BioLengthTest` |

A blank row is a miss. If a row is truly unused, keep it and mark the P3 ledger `N/A`.
