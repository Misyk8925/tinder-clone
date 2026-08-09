# Linear integration

This is the schema for how the workflow's tracking maps onto Linear — read it once before phase 1 on a new feature, and whenever the mapping is unclear mid-feature. It exists so every feature uses Linear the same way, the same reason `docs/contracts/conventions.md` exists for spec formats.

## Setup

The workflow needs the Linear MCP server (or an equivalent Linear integration) available as a tool. It doesn't need to be reachable in *this* session to plan the work — but a phase can't actually be gated in Linear without it.

- Check the tool list for Linear tools first. If present, use them.
- If not present but the environment supports adding one, the official server is a remote MCP endpoint: `claude mcp add --transport http linear-server https://mcp.linear.app/mcp`, then `/mcp` in a Claude Code session to complete OAuth. In this chat interface, that's a connector — search for it and let the user pick it rather than assuming.
- If Linear genuinely isn't available for this project (no workspace, user doesn't want it), use the fallback below and say so once, rather than silently defaulting to it.

## Object mapping

One **Linear Project** per feature is the default — Linear's own guidance is that a project earns its keep once work is "more than a handful of tickets," which is true of almost anything that reaches phase 2. For small work (the compressed process — see SKILL.md "Working with the user"), use a single **Issue** with sub-issues instead; skip milestones and Gate issues, just track risks/questions as sub-issues if any come up.

| Workflow concept | Linear object | Notes |
|---|---|---|
| Feature | Project | Name = feature name. Description = one-line pointer to `docs/features/<slug>/README.md`. |
| The five gated phases | 5 Project Milestones | "Phase 1 — Concept" … "Phase 5 — Release". Current phase = earliest milestone not yet complete. Bug hunt (phase 4.5) isn't a 6th milestone — its issues attach to the Phase 4 milestone, since that's when they happen; see `references/bug-hunt.md`. |
| Concept brief (EN + RU) | 2 Project Documents | Attached to the Project. Titles: "Concept — EN (B1)", "Concept — RU". Content follows `assets/templates/concept.en.md` / `concept.ru.md` section-for-section. |
| Hard gate (phase 1, phase 2) | 1 Gate issue per gate | e.g. "Gate: approve concept". Assigned to the approver. In milestone 1 (or 2). Status Todo until the user's explicit approval arrives in chat — then Done, with a comment recording who and when. Phase-2+ work issues are linked "blocked by" the relevant Gate issue where the tool supports it, so the dependency is visible in Linear itself, not just in this document. |
| Soft gate (phase 3) | 1 confirmation issue, or just a comment | Lighter — no blocking relation needed. |
| Risk | Issue, label `risk` + one of `risk-open` `risk-mitigated` `risk-accepted` `risk-closed` | Likelihood/impact go in the description (or map to Linear's Priority field if a rough single severity is enough). Assignee = owner. Due date = review-by date, for Accepted risks. See "Risk register" below. |
| Open question | Issue, label `question` — or an unresolved comment for something lighter | Assignee = who should answer. Done / resolved once answered, with the answer recorded in a comment. |
| Decision | Project Update | Linear's own chronological status-update feed on the Project. Post one at the end of each phase, and whenever a decision is made that isn't already obvious from a Gate issue closing. |
| Incident (live) | Issue, label `incident` | Created when an incident starts; carries the real-time timeline as comments. Linked to the durable postmortem file in the repo once written. |

If the project's Linear workspace already has custom workflow states that map more naturally to some of this (e.g. a team that added a "Mitigated" issue status instead of using labels), use those instead — the label scheme above is the portable default, not a mandate.

## Risk register, concretely

Don't build a table anywhere — the register *is* a saved Project view: filter on label `risk`, group by the `risk-*` sub-label. Set it up once per project (first feature that needs it), reuse after.

- **Open** (`risk-open`) — identified, no decision yet. Never acceptable at the phase-5 gate — see below.
- **Mitigated** (`risk-mitigated`) — a concrete change reduced likelihood or impact; say what, in the issue description or a comment.
- **Accepted** (`risk-accepted`) — deliberately not mitigated further. Needs an assignee (owner) and a due date (review-by) — Linear has both natively, use them rather than burying this in text.
- **Closed** (`risk-closed`, and the issue itself moved to the team's Done/Canceled category) — no longer applicable.

**Phase-5 gate check**: the `risk-open` view for this project must be empty before shipping. If something can't be fully triaged in time, it still needs a decision — at minimum Accepted with an owner — not silence.

## Decisions log, concretely

Post a Linear Project Update, not a markdown table row, whenever:
- A gate is approved (short summary + link to what was approved).
- A risk changes status.
- Something changed direction mid-phase and future-you would otherwise have to reconstruct why from a diff.

Use the update's health field honestly — "At risk" if there's an unresolved `risk-open` item close to a deadline, not just "On track" by default.

## No Linear available (fallback)

If this project doesn't use Linear, don't drop tracking — recreate the pre-Linear model on disk instead:

- `docs/features/<slug>/00-state.md` from `assets/templates/state.fallback.md` — phase, approvals, risk register, open questions, decisions log, all in one file, same semantics as the Linear mapping above (Open/Mitigated/Accepted/Closed still applies).
- `docs/features/<slug>/01-concept.en.md` and `01-concept.ru.md` from `assets/templates/concept.en.md` / `concept.ru.md`, in the repo instead of as Project Documents.

Say once, plainly, that this feature is running without Linear and why (not connected / project doesn't use it) — don't switch modes silently partway through a feature.

## What doesn't move to Linear

Contracts, Gherkin, the data catalog, the implementation plan and log, and the release checklist stay in the repo regardless of whether Linear is connected — they're read by CI and by agents doing the actual coding, versioned in the same commits as the code they describe, and diffed in the same PR. Linear is for the humans deciding and tracking; the repo is for the artifacts that get executed or compiled.
