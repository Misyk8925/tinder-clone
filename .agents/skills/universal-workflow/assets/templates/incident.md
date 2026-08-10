# Incident: <short title>

Feature: `<feature-slug>` · Release: `<version / git sha>`
Live incident tracker/channel: `<link>` (real-time coordination lived there; this file is the durable technical record)
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
| 1 | Executable regression test following project grouping, or Gherkin-like native default | 3/4 | | |
| 2 | New/corrected NFR: ... | 1 | | |
| 3 | Contract fix: ... | 2 | | |
| 4 | New alert: ... | 5 | | |
| 5 | Checklist line: ... | 5 | | |

The incident is not closed when service returns; it closes when actions are merged or recorded as owned risks with review dates in the selected tracker.
