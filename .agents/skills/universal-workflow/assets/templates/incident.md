# Incident: <short title>

Feature: `<feature-slug>` · Release: `<version / git sha>`
Linear incident issue: `<link>` (real-time tracking lived there; this file is the durable technical record)
Detected: <date, time, how — alert? user report?>
Resolved: <date, time>
User impact: <who, how many, for how long, what could they not do>
Severity: SEV1 (outage) | SEV2 (major degradation) | SEV3 (minor)

## Timeline

| Time | Event |
|------|-------|
|      | Deploy of `<sha>` started |
|      | First alert / first user report |
|      | Rollback triggered |
|      | Service restored |

## What happened

Plain description. No blame, no names attached to mistakes.

## Root cause

Not "a bug was introduced". Why did it get through — which guardrail was missing?

## Why we did not catch it earlier

| Stage | Should it have caught this? | Why it did not |
|-------|-----------------------------|----------------|
| Contract (phase 2) | | |
| Scenarios (phase 3) | | |
| Tests / review (phase 4) | | |
| Scan / smoke (phase 5) | | |
| Monitoring | | |

If monitoring did not catch it, that is a separate defect and it is more urgent than the original one.

## Resolution

Rolled back | Fixed forward — and why that was the right call.

## Actions — changes to earlier phases

| # | Action | Phase it lands in | Owner | Done |
|---|--------|-------------------|-------|------|
| 1 | Regression scenario `@regression` reproducing the failure | 3 | | |
| 2 | New/corrected NFR: ... | 1 | | |
| 3 | Contract fix: ... | 2 | | |
| 4 | New alert: ... | 5 | | |
| 5 | Checklist line: ... | 5 | | |

The incident is not closed when the service is back. It is closed when these are merged. Any row still not done gets raised as a Linear issue labeled `risk`/`risk-accepted` (with an owner and a review-by date) — an unmerged action sitting only in this file is exactly the kind of thing that gets forgotten by the next incident.
