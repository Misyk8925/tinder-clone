# Concept: Bio max length

Status: DRAFT · Slug: `bio-max-length`

## 1. Problem

Profile owners can save a bio longer than 500 characters. Reviewers cannot scan it; storage and feed cards overflow.

## 2. Contracts and behaviour (plain text)

In: profile id, bio string. Out: saved bio, or a rejection. Promises: bios longer than 500 characters are rejected. Does not: truncate, translate, or moderate content.

## 3. Functional requirements

| ID | Requirement | How we test it |
|---|---|---|
| FR-1 | Reject a bio longer than 500 characters | Native test: 501 chars → `BIO_TOO_LONG` |

## 4. Non-functional requirements

| ID | Requirement | How we measure it |
|---|---|---|
| NFR-1 | Validation p95 < 50 ms at 50 rps | Existing API bench on this endpoint |

## 5. Out of scope

- Content moderation, i18n, changing the 500 limit later

## 6. Suggested solution

Proposal until the phase-1 gate. Baseline: one length check on the existing profile write path.

Rejected: extra bio service — no FR/NFR needs it.

## 7. Open questions

| # | Question | Owner |
|---|---|---|
| Q1 | Should existing rows over 500 be migrated or left until next edit? | product |
