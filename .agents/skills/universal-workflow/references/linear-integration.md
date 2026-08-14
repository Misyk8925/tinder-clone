# Linear integration

This optional adapter maps the workflow onto Linear. Read it only when `references/project-profile.md` selects Linear because the project already uses it.

## Setup

A Linear-backed gate needs an available Linear integration.

- Check the tool list for Linear tools first. If present, use them.
- Do not install or configure an integration unless the user asks.
- If the project does not use Linear or it is unavailable, return to `references/project-profile.md`; do not force this adapter.

## Object mapping

One **Linear Project** per full feature is the default. For `bug-fix` and `compressed-small-change`, use one Issue (and sub-issues only when genuinely useful); skip milestones and feature Gate issues. See `references/mode-router.md` before creating tracking.

| Workflow concept | Linear object | Notes |
|---|---|---|
| Feature | Project | Name = feature name. Description = one-line pointer to `docs/features/<slug>/README.md`. |
| The five delivery phases | 5 Project Milestones | "Phase 1 — Concept" … "Phase 5 — Release". Targeted defect findings attach to Phase 4. |
| Concept brief | Project Document(s) | Use the language(s) selected by the project profile. |
| Hard gate (phase 1; combined phases 2+3) | 1 Gate issue per gate | Use "Gate: approve concept" and "Gate: approve contracts and executable behaviour". Keep the second gate open until the phase-3 combined verification passes and the user explicitly approves both artifacts. Implementation issues are blocked by that combined Gate where supported. |
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

Record the owner or approval source. An agent recommendation remains `Proposed`; do not post it as a decision until the owner answers or an existing durable decision is linked.

Use the update's health field honestly — "At risk" if there's an unresolved `risk-open` item close to a deadline, not just "On track" by default.

## Leaving this adapter

If Linear ceases to be the selected tracker, do not maintain duplicate state. Use the replacement tracker, or the repo fallback when none exists:

- `docs/features/<slug>/00-state.md` from `assets/templates/state.fallback.md` — phase, approvals, risk register, open questions, decisions log, all in one file, same semantics as the Linear mapping above (Open/Mitigated/Accepted/Closed still applies).
- concept document(s) in the language(s) selected by the project profile.

Say once, plainly, that this feature is running without Linear and why (not connected / project doesn't use it) — don't switch modes silently partway through a feature.

## What doesn't move to Linear

Canonical contracts, migrations, executable tests, implementation evidence, and release artifacts stay in their project repo locations. Linear links them; it never becomes their source of truth.
