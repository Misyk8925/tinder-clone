# Concept: Bio max length

Status: DRAFT · Slug: `bio-max-length`

Profile owners can save bios longer than 500 characters, so feed cards overflow. We reject bios over 500 characters on the existing profile write path.

## In scope

| ID | Part | Done when |
|---|---|---|
| FR-1 | Reject a bio longer than 500 characters | 501 chars → `BIO_TOO_LONG` |
| NFR-1 | Validation stays fast | p95 < 50 ms at 50 rps |

## Out of scope

| Item | Why |
|---|---|
| Content moderation | Separate feature |
| Changing the 500 limit | Fixed for v1 |
