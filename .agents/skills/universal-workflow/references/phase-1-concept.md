# Phase 1 — Concept

Goal: agree on *what problem we solve and roughly how*, before anyone argues about JSON field names.

## Steps

1. **Set up the Linear Project.** Name it after the feature; pick a matching kebab-case slug for the repo folder and git branch (Linear auto-generates a `branchName` on the Project's issues — Claude Code and Cursor already read it to check out the right branch, so keep the two aligned rather than inventing a second name). Create 5 milestones (Phase 1 … Phase 5) — bug hunt (phase 4.5) isn't a 6th one; its targeted-review issues attach to the Phase 4 milestone, since that's where they happen (see `references/bug-hunt.md`). For small work, skip the Project and use a single Issue with sub-issues instead — see `references/linear-integration.md`. If Linear isn't available, use the fallback there instead of skipping tracking.
2. **Create `docs/features/<slug>/README.md`** from `assets/templates/feature-readme.md`, linking the Linear Project. This is the only narrative markdown that lands in the repo — everything else there is contracts, scenarios, and code.
3. **Interview.** Ask only what you cannot infer, and ask it in one batch — do not interrogate over five turns. The categories below are what to *consider*, not a script to recite verbatim: skip anything already answered or obvious from context, but if you skip one because you inferred the answer, say what you assumed in the concept rather than deciding it silently.
   - **User & alternative** — who has this, what do they do today instead.
   - **Trigger & success** — what starts this, what tells you it worked — both in a test and after it ships to production.
   - **Boundaries** — what it must not do, what's explicitly out of scope for v1.
   - **Numbers** — load, latency, data volume, retention, compliance.
   - **Integration** — existing systems it talks to, anything it replaces or has to run alongside.
   - **Failure** — what happens when it partially fails, not just when someone misuses it.
   - **Stakeholders** — who besides the user is affected and needs to know when this ships (support, ops, another team's service).
   For a small, obvious feature most of these should already be inferable — the checklist is there so nothing gets silently skipped on a bigger one, not to turn every feature into an interrogation.
4. **Draft the concept** as two Linear Project Documents (EN B1, RU) — see `assets/templates/concept.en.md` / `concept.ru.md` for the section structure. Same structure, same numbering in both — a reader must be able to compare them section by section. (No Linear: write `01-concept.en.md` / `.ru.md` in the repo instead.)
5. **Self-check loop.** Before this draft goes anywhere near the user, verify it against reality — see below. This is not a second gate; it is quality control on the one gate that exists.
6. **Create the phase-1 Gate issue** ("Gate: approve concept"), assigned to the approver, status Todo.
7. **Stop.** Ask for approval.

## Self-check loop

```
draft → check against the project → check checkable claims → revise if needed
              ↑                                                       │
              └───────────── repeat, capped at 2 passes ──────────────┘
```

The gate has one shot per round-trip with the user — a draft that contradicts the existing codebase or rests on an unverified assumption costs a full cycle to catch. This loop catches it before the user sees the draft, not instead of the user seeing it.

**Check against the project.** Read what already exists before asking the user to approve something that quietly conflicts with it:

- Other Linear projects for this codebase — does this FR, NFR, or architecture choice contradict a decision already made there? (No Linear: other `docs/features/*/01-concept.en.md` files.)
- The actual stack — package manifests, `docker-compose`, existing services — does "suggested solution" fit it, or does it quietly introduce a new database, framework, or messaging system? Introducing something new is fine; introducing it *silently* is not — call it out as a decision, not an assumption.
- `docs/contracts/conventions.md`, if the project has reached phase 2 before on another feature — align terminology and format expectations now rather than discovering the mismatch in phase 2.

**Check checkable claims — light research, not a research project.** Pull out only the specific, consequential claims a wrong guess would be expensive for: a feasibility assumption in "Suggested solution", a number in an NFR, a limit or behavior of an external system, a market/competitor claim if the concept is business-facing. One to three targeted searches or a quick look at the relevant docs — enough to confirm or correct, not an open-ended investigation. Skip anything Claude already knows cold and that isn't time-sensitive; the point is catching what could be wrong or stale, not re-deriving common knowledge.

**Cap it at two passes.** Unlike phase 4, there is no test suite that turns green — "is this concept good enough" doesn't have an objective stop signal, so an uncapped loop just turns into stalling. After two passes, whatever is still unresolved needs a home — see below.

**Not everything the loop turns up is a question for the user.** If it's a gap in the spec — "should retention be 12 or 24 months?" — that's an open question: a Linear issue labeled `question`, assigned to whoever can answer. If it's a known uncertainty with no single right answer to ask for — "the geocoding API's rate limit isn't documented, could throttle under peak load" — that's a risk: a Linear issue labeled `risk`/`risk-open`, with a likelihood, impact, and either a mitigation plan or an explicit accept-with-owner. It doesn't block the gate; it travels with the feature until someone closes it.

**Log it briefly** — a comment on the phase-1 Gate issue (or on the concept Documents), one or two lines, not a full audit trail:

```
Pre-gate checks: suggested solution cross-checked against the `tenant-billing`
project (no conflict) and against docker-compose.yml (Postgres already in use,
no new dependency). NFR-2's 99.5% target checked against the current uptime of
the service it depends on — revised from 99.9% after that check.
```

## English at B1 level

The English version is written to be read fast, not to sound impressive.

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

- Both language versions exist and match.
- Every FR is testable; every NFR has a number.
- The suggested solution is the cheapest one that satisfies every FR/NFR — anything pricier than the obvious baseline traces to a specific FR/NFR number, not to "best practice."
- At least one rejected alternative is documented, with the number that made it too expensive or the number it failed to meet.
- The suggested solution has been checked against the existing project (stack, conventions, other Linear projects) — no silent conflicts.
- Checkable claims (feasibility, numbers, external limits) were checked, or explicitly raised as risks.
- Open questions are raised as Linear `question` issues, not silently answered.
- The user has written an explicit approval; the phase-1 Gate issue is closed with a comment recording it.
