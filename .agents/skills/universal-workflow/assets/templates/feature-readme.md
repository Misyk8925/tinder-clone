# <feature-slug>

Tracked in Linear: **<link to the Linear Project>**

That's where the problem statement, requirements, decisions, approvals, risks and open questions live — this folder holds only the artifacts that get read by tooling or written into code. If you're an agent picking up this feature, read the Linear project first for *why*; read the folders below for *what to build against*.

(No Linear on this project: the equivalent content is in `00-state.md`, `01-concept.en.md` / `01-concept.ru.md`, and `bugs/` in this folder — see `references/linear-integration.md` and `references/bug-hunt.md` in the skill for why.)

## What's here

| Folder | What it is | Read it when |
|---|---|---|
| `02-contracts/` | OpenAPI/AsyncAPI specs + human-readable mirrors, data catalog | Implementing any endpoint, event, or schema change |
| `03-behaviour/` | Gherkin scenarios — the executable acceptance criteria | Writing or running tests |
| `04-implementation/` | Slice plan and iteration log | Picking up work mid-feature |
| `05-release/` | Release checklist, incident postmortems | Shipping, or after something broke |
| `bugs/` | Confirmed-bug write-ups (repro, root cause, severity, regression test) — **No Linear only**; with Linear these are issues instead | Fixing a bug found by targeted review, or checking one's still open |

## Current phase

**<Phase N — name>** — see the Linear project for the live status; this line is a breadcrumb, not the source of truth, and may be stale.
