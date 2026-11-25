# Phase 1 — Concept

Goal: agree on *what problem we solve and roughly how*, before anyone argues about JSON field names.

Show the phase-1 ledger from `references/phase-ledger.md` before asking for approval. Every P1 row is `Done`, `N/A`, `Blocked`, or `Deferred`; a missing row is a protocol failure.

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
   For a small, obvious feature most of these should already be inferable — say so per category on the ledger (`inferred: …`). The checklist exists so a category cannot disappear; do not skip a category by not mentioning it, and do not turn every feature into an interrogation.
5. **Split discovery only when needed.** If several independent unknown branches cannot be resolved coherently in the current decision context, create Wayfinder-style research, prototype, or decision issues. Each issue answers one named question, records dependencies, produces one reviewable artifact, and stops without implementing production code. A prototype is disposable evidence for a decision, not an early implementation. Merge the resulting facts and owner decisions back into one concept; the split never bypasses the phase-1 approval gate.
6. **Confirm shared understanding.** Summarize the problem, observable behaviour, boundaries, approved choices with their sources, proposed recommendations, rejected alternatives, and unresolved questions. Ask the owner to correct this summary before turning it into the concept draft. Do not relabel proposed recommendations as accepted choices. This confirmation is not the approval gate.
7. **Draft the concept** in the language(s) selected by the project profile, using `assets/templates/concept.*.md`. The document has only a 2–4 sentence summary, an in-scope table, and an out-of-scope table — see *Concept format* below. If several language versions are requested, keep their rows and IDs aligned.
8. **Self-check loop.** Before this draft goes anywhere near the user, verify it against reality — see below. This is not a second gate; it is quality control on the one gate that exists.
9. **Create the phase-1 gate item** ("Gate: approve concept") in the selected tracker and assign the approver when supported.
10. **Stop.** Ask for approval. Once given, record it in the gate item and a tracker update. Do not start phase 2 in the same turn.

## Self-check loop

```
draft → check against the project → check checkable claims → revise if needed
              ↑                                                       │
              └───────────── repeat, capped at 2 passes ──────────────┘
```

The gate has one shot per round-trip with the user — a draft that contradicts the existing codebase or rests on an unverified assumption costs a full cycle to catch. This loop catches it before the user sees the draft, not instead of the user seeing it.

**Check against the project.** Read what already exists before asking the user to approve something that quietly conflicts with it:

- Other tracker items and feature indexes for this codebase — does this FR, NFR, or architecture choice contradict a decision already made there?
- The actual stack — package manifests, `docker-compose`, existing services — does the approach fit it, or does it quietly introduce a new database, framework, or messaging system? Introducing something new is fine; introducing it *silently* is not — call it out as a decision, not an assumption.
- `docs/contracts/conventions.md`, if the project has reached phase 2 before on another feature — align terminology and format expectations now rather than discovering the mismatch in phase 2.

**Check checkable claims — light research, not a research project.** Pull out only the specific, consequential claims a wrong guess would be expensive for: a feasibility assumption in the approach, a number in an NFR, a limit or behavior of an external system, a market/competitor claim if the concept is business-facing. One to three targeted searches or a quick look at the relevant docs — enough to confirm or correct, not an open-ended investigation. Skip anything Claude already knows cold and that isn't time-sensitive; the point is catching what could be wrong or stale, not re-deriving common knowledge.

**Cap it at two passes.** Unlike phase 4, there is no test suite that turns green — "is this concept good enough" doesn't have an objective stop signal, so an uncapped loop just turns into stalling. After two passes, whatever is still unresolved needs a home — see below.

**Not everything the loop turns up is a question for the user.** A missing owner decision becomes an open question in the selected tracker. A known uncertainty with no immediate answer becomes an owned risk with likelihood, impact, and mitigation or explicit acceptance. It travels with the feature until resolved.

**Log it briefly** — a comment on the phase-1 gate item or concept document, one or two lines, not a full audit trail:

```
Pre-gate checks: approach cross-checked against the `tenant-billing`
project (no conflict) and against docker-compose.yml (Postgres already in use,
no new dependency). NFR-2's 99.5% target checked against the current uptime of
the service it depends on — revised from 99.9% after that check.
```

## Language and readability

**The concept is written in simple technical English.** Another language version is added only when the project profile or user asks for it, and it follows the same rules. Write to be read fast, not to sound impressive.

- Short sentences. One idea each.
- Common words: "use" not "utilise", "start" not "commence", "about" not "approximately".
- Active voice: "The service sends an email" not "An email is sent by the service".
- Technical nouns stay as they are (webhook, idempotency, JWT) — B1 means simple *grammar and general vocabulary*, not dumbed-down domain terms.
- If a sentence needs a comma to survive, split it.

Bad: "Subsequent to the reception of an inbound inquiry, the system shall undertake the generation of a preliminary quotation."
Good: "When a new inquiry arrives, the system creates a draft quote."

## Concept format

The concept is short. Three parts, nothing else:

```
<2–4 sentences: the problem and the chosen approach>

## In scope
| ID | Part | Done when |
| FR-1  | Reject a bio longer than 500 characters | 501 chars → BIO_TOO_LONG |
| NFR-1 | Validation stays fast                   | p95 < 50 ms at 50 rps    |

## Out of scope
| Item | Why |
| Content moderation | Separate feature |
```

- **FR rows** — what the system does; one row, one testable behaviour.
- **NFR rows** — how well; "Done when" always has a number. Check performance, scale, availability, security, privacy, observability, cost — add a row only when one applies.
- **Out of scope** — the cheapest table to write and the most expensive to omit.

Do not add problem essays, prose contracts, architecture sections, or diagrams to the document.

## Approach and rejected alternatives

Not a document section. The summary names the approach in one sentence. **It is the cheapest thing that satisfies every in-scope row**, in engineering time and operating cost; anything pricier must point to a specific row ID, not "best practice" or "we may need it later".

Put rejected alternatives in the gate request, one line each, with the row it failed or the cost that ruled it out:

```
Rejected: Kafka between the two — ~50 msg/min does not justify a broker.
Rejected: serverless — cold starts break NFR-1.
```

## Exit criteria

- The concept is in simple technical English: short sentences, common words, active voice, domain terms unchanged.
- Any extra language version required by the project profile exists and has the same rows and IDs.
- The concept is only a 2–4 sentence summary, an in-scope table, and an out-of-scope table.
- Every FR row is testable; every NFR row has a number.
- The approach is the cheapest one that satisfies every row — anything pricier traces to a specific row ID.
- At least one rejected alternative is in the gate request, with the row it failed or the cost that ruled it out.
- The approach has been checked against the existing project (stack, conventions, prior tracker decisions) — no silent conflicts.
- Checkable claims (feasibility, numbers, external limits) were checked, or explicitly raised as risks.
- Open questions are recorded in the selected tracker, not silently answered.
- The decision tree was shown before any "key decisions," concept draft, or chosen architecture; every node distinguishes `Fact`, `Proposed`, `Open`, and `Approved`.
- At least one frontier round occurred while consequential choices remained unresolved; if none remained, the response explicitly said so and cited the durable sources for every approved choice.
- Consequential choices came from the owner; agent-established facts carry evidence, and no implementation agent is left to guess an unresolved decision.
- Any split research or prototype issue answers one named question and has been folded back into the single concept.
- The owner confirmed the shared-understanding summary before the approval request.
- The user has written an explicit approval; the selected phase-1 gate item records it.
- The phase-1 ledger is complete and was shown with the approval request; non-`Done` rows have reasons.
- The phase-exit review from `references/phase-exit-review.md` is folded into that same gate request, not sent as a second stop.
