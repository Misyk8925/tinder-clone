# Phase 1 — Concept

Goal: agree on *what problem we solve and roughly how*, before anyone argues about JSON field names.

## Steps

1. **Set up the selected tracker.** Resolve `references/project-profile.md`. For full feature delivery, create its feature/project container and phase milestones; shorter modes do not enter phase 1. Use `references/linear-integration.md` only when Linear is the selected adapter.
2. **Create or update the feature index** in the project's established location. If none exists, use `docs/features/<slug>/README.md` from `assets/templates/feature-readme.md`. Link the tracker and canonical contracts/tests; do not copy them.
3. **Build and expose the decision tree from facts.** Read the codebase, existing contracts, feature history, tracker, and relevant external evidence first. Separate facts the agent can establish from consequential choices only the owner can make. Before presenting "key decisions," a concept draft, or a chosen architecture, show a compact dependency tree whose nodes are marked:
   - `Fact` — established by the agent, with the code/artifact evidence;
   - `Approved` — explicitly answered by the owner or backed by a cited durable prior decision;
   - `Proposed` — the agent's recommendation, still awaiting an owner choice;
   - `Open` — not yet ready or lacking sufficient evidence.
   A recommendation does not become `Approved` because it looks obvious or is the cheapest option. A decision becomes ready only when its prerequisites are facts or approved choices.
4. **Interview the frontier.** Ask only the ready `Proposed` or `Open` product, scope, architecture, UX, cost, or risk choices that cannot be inferred. Give the evidence, a recommended answer, and the meaningful tradeoff for each. Run at least one frontier round whenever any consequential choice remains unresolved. For a small feature, ask the ready questions in one batch. For a large uncertain feature, use a few bounded rounds: after each answer, update and re-show the tree, then ask only the newly ready questions. If every consequential choice was already approved, state that there is no unresolved frontier and cite each decision source instead of asking ceremonial questions. Do not turn the category checklist into a script or ask the user for facts the project can answer.
   - **User & alternative** — who has this, what do they do today instead.
   - **Trigger & success** — what starts this, what tells you it worked — both in a test and after it ships to production.
   - **Boundaries** — what it must not do, what's explicitly out of scope for v1.
   - **Numbers** — load, latency, data volume, retention, compliance.
   - **Integration** — existing systems it talks to, anything it replaces or has to run alongside.
   - **Failure** — what happens when it partially fails, not just when someone misuses it.
   - **Stakeholders** — who besides the user is affected and needs to know when this ships (support, ops, another team's service).
   For a small, obvious feature most of these should already be inferable — the checklist is there so nothing gets silently skipped on a bigger one, not to turn every feature into an interrogation.
5. **Split discovery only when needed.** If several independent unknown branches cannot be resolved coherently in the current decision context, create Wayfinder-style research, prototype, or decision issues. Each issue answers one named question, records dependencies, produces one reviewable artifact, and stops without implementing production code. A prototype is disposable evidence for a decision, not an early implementation. Merge the resulting facts and owner decisions back into one concept; the split never bypasses the phase-1 approval gate.
6. **Confirm shared understanding.** Summarize the problem, observable behaviour, boundaries, approved choices with their sources, proposed recommendations, rejected alternatives, and unresolved questions. Ask the owner to correct this summary before turning it into the concept draft. Do not relabel proposed recommendations as accepted choices. This confirmation is not the approval gate.
7. **Draft the concept** in the language(s) selected by the project profile. Use the concept templates for section structure when useful. If several language versions are requested, keep their structure and requirement numbering aligned.
8. **Self-check loop.** Before this draft goes anywhere near the user, verify it against reality — see below. This is not a second gate; it is quality control on the one gate that exists.
9. **Create the phase-1 gate item** ("Gate: approve concept") in the selected tracker and assign the approver when supported.
10. **Stop.** Ask for approval.

## Self-check loop

```
draft → check against the project → check checkable claims → revise if needed
              ↑                                                       │
              └───────────── repeat, capped at 2 passes ──────────────┘
```

The gate has one shot per round-trip with the user — a draft that contradicts the existing codebase or rests on an unverified assumption costs a full cycle to catch. This loop catches it before the user sees the draft, not instead of the user seeing it.

**Check against the project.** Read what already exists before asking the user to approve something that quietly conflicts with it:

- Other tracker items and feature indexes for this codebase — does this FR, NFR, or architecture choice contradict a decision already made there?
- The actual stack — package manifests, `docker-compose`, existing services — does "suggested solution" fit it, or does it quietly introduce a new database, framework, or messaging system? Introducing something new is fine; introducing it *silently* is not — call it out as a decision, not an assumption.
- `docs/contracts/conventions.md`, if the project has reached phase 2 before on another feature — align terminology and format expectations now rather than discovering the mismatch in phase 2.

**Check checkable claims — light research, not a research project.** Pull out only the specific, consequential claims a wrong guess would be expensive for: a feasibility assumption in "Suggested solution", a number in an NFR, a limit or behavior of an external system, a market/competitor claim if the concept is business-facing. One to three targeted searches or a quick look at the relevant docs — enough to confirm or correct, not an open-ended investigation. Skip anything Claude already knows cold and that isn't time-sensitive; the point is catching what could be wrong or stale, not re-deriving common knowledge.

**Cap it at two passes.** Unlike phase 4, there is no test suite that turns green — "is this concept good enough" doesn't have an objective stop signal, so an uncapped loop just turns into stalling. After two passes, whatever is still unresolved needs a home — see below.

**Not everything the loop turns up is a question for the user.** A missing owner decision becomes an open question in the selected tracker. A known uncertainty with no immediate answer becomes an owned risk with likelihood, impact, and mitigation or explicit acceptance. It travels with the feature until resolved.

**Log it briefly** — a comment on the phase-1 gate item or concept document, one or two lines, not a full audit trail:

```
Pre-gate checks: suggested solution cross-checked against the `tenant-billing`
project (no conflict) and against docker-compose.yml (Postgres already in use,
no new dependency). NFR-2's 99.5% target checked against the current uptime of
the service it depends on — revised from 99.9% after that check.
```

## Language and readability

Write every requested language version to be read fast, not to sound impressive.

- Short sentences. One idea each.
- Common words: "use" not "utilise", "start" not "commence", "about" not "approximately".
- Active voice: "The service sends an email" not "An email is sent by the service".
- Technical nouns stay as they are (webhook, idempotency, JWT) — B1 means simple *grammar and general vocabulary*, not dumbed-down domain terms.
- If a sentence needs a comma to survive, split it.

Bad: "Subsequent to the reception of an inbound inquiry, the system shall undertake the generation of a preliminary quotation."
Good: "When a new inquiry arrives, the system creates a draft quote."

## Requirements

**FR** — what the system does. Each one is a sentence with a subject and a verb, and you can imagine a test for it.

```
FR-1  The system accepts an inquiry by email, web form, or WhatsApp.
FR-2  The system extracts customer name, address, and job description from the inquiry.
FR-3  The user can edit any extracted field before the quote is sent.
```

**NFR** — how well. Each one has a number and a way to measure it.

```
NFR-1  p95 latency for quote generation < 5 s (measured at the API edge).
NFR-2  Availability 99.5% monthly, excluding announced maintenance.
NFR-3  Customer data stored in the EU; deleted 24 months after last activity.
NFR-4  No PII in logs.
```

Categories worth checking every time: performance, scale, availability, security, privacy/GDPR, observability, cost, maintainability.

## Suggested solution

Cover: components and their responsibilities, data flow (a numbered walk-through of the main path), storage and schema sketch, external dependencies, failure behaviour, and **rejected alternatives**.

**Start from the cheapest thing that satisfies every FR and NFR exactly as validated, not the most impressive one.** Requirements are fixed by now — this step is about how cheaply they can be met, in engineering time and in operating cost, not about showcasing architecture. "Cheapest" still has to clear every NFR (security, availability, whatever's in the list) — this is not license to drop one quietly to save money; it's license to stop paying for anything a validated requirement doesn't ask for. Every increment of cost or complexity past that baseline needs to point at a specific FR/NFR number. "Best practice," "more scalable," and "we'll probably need this later" are not numbers — if the honest answer is "no requirement forces this," cut it, or turn it into an explicit, named decision the user can see and question.

The rejected alternatives section is where that pricing shows up, not an afterthought box to check. It is the one that protects you six months later when someone asks "why didn't we just use X?" — because the answer is already written down.

```
Chosen: single service + Postgres outbox + worker.
Rejected: Kafka between the two — the load (~50 msg/min) does not justify running a broker.
Rejected: serverless functions — cold starts break NFR-1.
```

**Draw it when there's enough to draw.** If the components and data flow can't be held in your head after one read of the numbered walkthrough — more than a couple of services, more than one external system, a non-trivial flow — sketch an architecture diagram alongside the prose, not instead of it (Mermaid in the doc if the renderer supports it, otherwise a plain image). For a single-service, single-flow feature, the prose is enough; a diagram would just restate it in boxes. When the choice between two designs genuinely isn't obvious, a quick side-by-side sketch of both candidates before picking is worth more than the diagram itself — it's the difference between "rejected alternatives" being a real comparison and being a one-line justification written after the fact.

## Exit criteria

- Every language version required by the project profile exists and matches the others structurally.
- Every FR is testable; every NFR has a number.
- The suggested solution is the cheapest one that satisfies every FR/NFR — anything pricier than the obvious baseline traces to a specific FR/NFR number, not to "best practice."
- At least one rejected alternative is documented, with the number that made it too expensive or the number it failed to meet.
- The suggested solution has been checked against the existing project (stack, conventions, prior tracker decisions) — no silent conflicts.
- Checkable claims (feasibility, numbers, external limits) were checked, or explicitly raised as risks.
- Open questions are recorded in the selected tracker, not silently answered.
- The decision tree was shown before any "key decisions," concept draft, or chosen architecture; every node distinguishes `Fact`, `Proposed`, `Open`, and `Approved`.
- At least one frontier round occurred while consequential choices remained unresolved; if none remained, the response explicitly said so and cited the durable sources for every approved choice.
- Consequential choices came from the owner; agent-established facts carry evidence, and no implementation agent is left to guess an unresolved decision.
- Any split research or prototype issue answers one named question and has been folded back into the single concept.
- The owner confirmed the shared-understanding summary before the approval request.
- The user has written an explicit approval; the selected phase-1 gate item records it.
