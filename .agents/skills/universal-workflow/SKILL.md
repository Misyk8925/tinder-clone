---
name: universal-workflow
description: "Use this delivery workflow only when implementing one of three scopes: a full feature, a bug fix, or a small non-trivial change that still needs acceptance and verification. Route first to full-feature-delivery, bug-fix, or compressed-small-change. It provides gated design for full features, confirmation/diagnosis/regression proof for bugs, and a one-slice path for small changes, followed by risk-based tests at applicable levels, targeted review, and scoped release evidence. Show a visible phase ledger for every named sub-step; never silently omit one. After each run phase, do a short phase-exit review without adding extra approval gates. Follow the project's acceptance-test convention; when none exists, default to Gherkin-like Given/When/Then tests in the native test framework. Do not use for read-only review/audit, standalone RCA without a fix, explanation, research, pure planning/specification, trivial edits, or release-only operations unless explicitly requested."
---

# Universal Workflow

A delivery workflow with three sizes: full feature delivery, bug fix, and compressed small change. The point of its gates and evidence is simple: **code is the most expensive place to discover a misunderstanding**, while small and corrective work should not inherit feature-sized ceremony.

## Route first

Before creating artifacts, read `references/mode-router.md` and choose exactly one mode:

- `full-feature-delivery` — run the complete phased workflow;
- `bug-fix` — reproduce, establish root cause and regression evidence, fix, review, and validate the affected release path;
- `compressed-small-change` — one compact scope record, one observable slice, applicable checks, and targeted review.

If the task is outside these three delivery modes, do not use this skill. A mode may be promoted when discovery reveals more risk or scope; never keep the compressed mode by silently dropping required decisions or evidence.

The five-phase sequence below is the `full-feature-delivery` path. `bug-fix` and `compressed-small-change` follow their shorter routes in `references/mode-router.md` and reuse only the phase rules that apply to their risk. After routing, show the phase ledger from `references/phase-ledger.md`: every named sub-step is `Done`, `N/A`, `Blocked`, `Deferred`, or `Mode-omit`. A missing row is a protocol failure.

Resolve the project profile from repository instructions and established conventions. Read `references/project-profile.md`, then use `references/efficient-project-search.md` before material discovery, diagnosis, or planning. Use Augment Context Engine and Serena when they are already available and fit the search question; neither is a prerequisite. Use `references/linear-integration.md` only when Linear is the selected tracker.

## Core rules

1. **Never skip required decisions.** In full feature delivery, no technical contracts before concept approval and no implementation before the combined phase-2/3 approval. Shorter modes use their own evidence and approval rules.
2. **Full feature delivery has two hard gates:** phase 1 approves the concept; the gate after phase 3 approves the combined technical contracts and executable behavioural contracts. Phase 2 has no separate approval. Stop at each gate and wait for explicit user approval.
3. **Use the project's tracker; keep executable artifacts in canonical repo locations.** Tracker state records approvals, risks, questions, and decisions. Specs, migrations, tests, and plans stay with the code that executes or validates them. Link between the two; do not duplicate canonical artifacts merely to fit this workflow.
4. **Treat Linear as an adapter, not a prerequisite.** Use it only when the project already uses it and the integration is available. Otherwise use the existing tracker or the repo fallback from `references/project-profile.md`.
5. **Failure moves backwards through the phases, never sideways.** A red test, failed deploy, or incident returns to the phase that could have prevented it, and the fix adds executable regression evidence at the closest applicable level.
6. **Risks are logged where they're found, not carried in your head.** Any phase can raise or close one in the selected tracker. Before phase 5 ships, every risk is Mitigated, Accepted with an owner, or Closed; Open risk does not ship.
7. **Use the project's documentation language.** If none is established, use concise English. Add translations only when the project profile or user requests them; language is an adapter, not a universal gate.
8. **Use one bug lifecycle:** a lead becomes `confirmed` when reproducible evidence shows actual behaviour violating established expected behaviour, `diagnosed` when the root-cause mechanism is understood, and `fixed` only when a faithful regression test fails on the broken code and passes on the fix. "Sometimes happens" remains a lead — see `references/targeted-defect-review.md`.
9. **Once requirements are validated, design starts from the cheapest thing that still satisfies every FR and NFR — not the most impressive, scalable, or familiar one.** Cheapest in engineering time and operating cost both, never cheapest by quietly dropping a requirement. Every increment of cost or complexity past that baseline has to trace to a specific FR/NFR number; "best practice" and "we might need it later" don't count. This is what rejected alternatives are for.
10. **Agents establish facts efficiently; people own consequential choices.** Follow `references/efficient-project-search.md` to inspect the code, contracts, consumers, tracker, and external evidence before asking. Ask the user only about real product, scope, architecture, UX, cost, or risk choices, and record the decision instead of letting an implementation agent guess it later.
11. **Expose decisions before declaring them.** In full feature delivery, before presenting "key decisions," a concept draft, or a chosen architecture, show the compact dependency tree and its current frontier. Mark agent-established facts with evidence, recommendations as `Proposed`, and owner choices as `Approved` only after an explicit answer or a cited durable prior decision. Run at least one frontier round whenever a consequential choice remains unresolved. If none remains, say so and cite the sources instead of asking ceremonial questions.
12. **Keep agent work bounded by observable slices and durable artifacts.** A slice should normally fit in one fresh implementation context for code, tests, and its evidence report, followed by a separate fresh-context review. When a session or agent changes, hand off the ticket, approved artifacts, code, test evidence, and unresolved questions — not a transcript or an unverified summary. Re-slice work that cannot be reviewed as one coherent result.
13. **Never silently skip a phase sub-step.** Conditional work still gets a ledger row. Read `references/phase-ledger.md` and show the table at routing, whenever a row is not `Done`, and at every phase/slice exit and gate. `N/A` needs a change-specific reason; `Blocked` is not passed; `Deferred` needs an owner agreement. Omitting the row is the same class of failure as skipping a hard gate.
14. **Review the phase before leaving it, without adding extra gates.** After each run phase, follow `references/phase-exit-review.md`. Fold the review into the existing phase-1 and combined 2+3 gate requests. After phase 2, continue to phase 3 unless an owner decision appeared. Phase 4 already reviews each slice; the phase-exit session is the combined-diff review. Shorter modes get one exit review, not five.

## Where things live

**In the selected tracker** (use `references/linear-integration.md` only for projects that use Linear):

| Workflow state | Tracker representation |
|---|---|
| Phase and approvals | Phase milestones/status plus one gate item per hard gate |
| Concept brief | Project document(s) in the language(s) selected by the project profile |
| Risk register | Issues labeled `risk`, filtered in a saved Project view |
| Open questions | Issues labeled `question`, or unresolved comments |
| Decisions log | Tracker updates or decision records |
| Incident tracking (live) | An issue labeled `incident`, linked to the postmortem file |
| Bug found (targeted review) | Bug item with severity and the fixed evidence shape from `references/targeted-defect-review.md` |
| Phase ledger (latest) | Gate/issue comment or compact issue body; repo copy in the feature index / plan / bug item |

**In the repo.** Two different scopes — don't nest one inside the other:

```
docs/
├── contracts/
│   └── conventions.md          # PROJECT-ROOT, one per codebase — spec formats and naming.
│                                # Written once by whichever feature needs it first;
│                                # every later feature reads it, never re-decides or copies it.
├── quality/
│   └── metrics.md              # PROJECT-ROOT, per service/product stream — rolling SLO, delivery,
│                                # test, security, data, alert, and accessibility evidence
│   └── gates.md                # PROJECT-ROOT — deterministic CI quality gates and approved exceptions
└── features/
    └── <feature-slug>/          # everything below is per-feature, never shared
        ├── README.md            # links the selected tracker and canonical artifacts
        ├── 02-contracts/
        │   └── README.md         # links canonical specs/migrations/docs + compatibility impact
        ├── 03-behaviour/
        │   └── README.md         # traceability + links to executable acceptance tests
        ├── 04-implementation/
        │   ├── plan.md           # task breakdown
        │   └── log.md            # iteration log: what failed, what changed
        │   └── qa-metrics.md     # evidence-backed quality snapshot, from phase 4 through release
        ├── bugs/                  # repo fallback only — see targeted-defect-review.md
        │   └── <bug-slug>.md
        └── 05-release/
            ├── checklist.md
            └── incidents/         # only if something went wrong
                └── <date>-<slug>.md
```

Use the project's canonical contract locations. The feature folder indexes or links them; it never copies an OpenAPI document, event schema, or migration. If no contract convention exists, phase 2 supplies a portable spec-plus-readable-view default.

## The phases

Read the matching reference file when entering a phase; it contains artifacts, questions, checks, and exit criteria. Before leaving the phase, fill every ledger row for that phase from `references/phase-ledger.md` and run the phase-exit review from `references/phase-exit-review.md`.

This table applies to `full-feature-delivery`:

| # | Phase | Reference | Gate | Exit review |
|---|-------|-----------|------|-------------|
| 1 | Concept | `references/phase-1-concept.md` | **Manual approve** | Folded into that gate |
| 2 | API & event contracts | `references/phase-2-contracts.md` | Continue to phase 3 | Yes, no extra stop |
| 3 | Executable behavioural contracts | `references/phase-3-behaviour.md` | **Manual approve phases 2 + 3** | Folded into that gate |
| 4 | Implementation loop | `references/phase-4-implementation.md` | Loop until green | Per slice + combined diff |
| 5 | Release | `references/phase-5-release.md` | Ship | Ship-evidence check |

Templates in `assets/templates/` are short filled samples for `bio-max-length`. Copy the shape, replace the sample. Ledger tables show two or three rows only; the closed ID list stays in `references/phase-ledger.md`. Tracker and project conventions come from `references/project-profile.md`.

### Phase 1 — Concept (gate)

Create the full-feature tracking container selected by the project profile. Write the concept brief in the selected project language(s), covering:

- **Problem** — who has it, when, what it costs them today. Concrete, not "users want a better experience".
- **Contracts and behaviour in plain text** — what goes in, what comes out, what the system promises, what it refuses to do. No JSON yet — prose that a non-engineer could check.
- **Functional requirements** — numbered FR-1, FR-2…, each testable.
- **Non-functional requirements** — numbered NFR-1…, each with a number attached (latency, throughput, availability, data retention, security, cost). "Fast" is not an NFR; "p95 < 300 ms at 50 rps" is.
- **Out of scope** — the cheapest section to write and the most expensive to omit.
- **Suggested solution** — the cheapest design that satisfies every FR/NFR exactly as validated, not the most impressive one: components, data flow, storage, external systems. Anything pricier than the obvious baseline needs to trace to a specific FR/NFR number, not "best practice" — and *at least one rejected alternative with the number it failed to meet*. A design with no rejected alternative was not a decision. Draw a diagram alongside the prose only once there's enough to draw — a single-service, single-flow design doesn't need one.

Start discovery from project facts, then show the remaining uncertainty as a small tree of dependent decisions with `Fact`, `Proposed`, `Open`, and `Approved` status. Before drafting a suggested solution or listing "key decisions," ask only the frontier questions that have enough evidence to answer now; after each bounded round, update the visible tree and confirm the shared understanding. A recommendation is never an approved decision without an explicit owner answer or a cited durable prior decision. For a large idea with several independent unknown branches, use a Wayfinder-style split: create focused research, prototype, or decision issues that each answer one named question, then merge their evidence back into one concept. These issues do not bypass the phase-1 gate. Interview against the category checklist (user, trigger/success, boundaries, numbers, integration, failure, stakeholders), say what was inferred, and run the short self-check loop in `references/phase-1-concept.md` before asking for approval.

Then stop and ask for approval. Once given, record the approval in the selected gate item and tracker update. Do not start phase 2 in the same turn.

### Phase 2 — API & event contracts

Update contracts in their canonical project locations and link them from the feature index. Follow the project's spec and readable-documentation convention. If none exists, default to a machine-readable spec plus a generated or maintained readable view. A real migration remains canonical for schema changes; never copy it into the feature folder. Include error and edge cases, and record unresolved uncertainty in the selected tracker. Do not request approval yet; continue to phase 3.

### Phase 3 — Behavioural contracts

Create executable acceptance criteria for every FR and phase-2 error path. Follow an established project convention. When none exists, write Gherkin-like Given/When/Then tests in the native test framework; do not introduce Cucumber solely for this workflow. After phase 3, run one combined check across requirements, contracts, errors, and acceptance tests. Then show phases 2 and 3 as one package and request one explicit approval before implementation.

### Phase 4 — Implementation loop

For full features, Phase 3 supplies the outside-in acceptance check for each slice. Bug fixes use regression evidence; compressed mechanical changes may use other deterministic evidence. Choose additional test levels from changed risks; no fixed pyramid applies.

```
plan → primary evidence/red where applicable → code + applicable tests → green/proven → review → refactor
   ↑                                                                                                  │
   └──────────────────────────── if anything fails, back to plan ──────────────────────────────────────┘
```

Run this per slice, not per feature — a slice is the smallest observable behaviour that makes at least one acceptance check pass and can normally be implemented, tested, and reported from one fresh implementation context. Record its dependencies and classify it `HITL` when a human choice or manual product check remains; use `AFK` only for low-risk, fully specified, deterministically verified work, never as permission to auto-merge. Select unit, component, integration, contract, system/e2e, and non-functional checks only where the changed risks need them. Review each slice from a separate fresh context against both the approved specification and the project's engineering standards. Hand off between sessions through the ticket, repo artifacts, code, test evidence, and unresolved questions. Full rules are in `references/phase-4-implementation.md`.

Log failed iterations in `04-implementation/log.md` for full features, or in the compact issue/plan for shorter modes. Record risk-selected test evidence as it appears. Review each slice for specification and engineering fit; that review includes targeted defect discovery using `references/targeted-defect-review.md`. Full feature delivery also gets one final review across the combined diff. Exit according to the mode-specific criteria in `references/phase-4-implementation.md`.

### Phase 5 — Release

Build → security scan → deploy → smoke tests → monitoring → docs. Each applicable step gates the next. **Before shipping: the selected tracker's Open-risk view is empty and no blocker-severity bug remains in scope.** Complete release metrics only after their observation window; until then use “not yet observed,” not “passed.”

**If a step fails, the loop goes backwards, and where to depends on what failed** — a build failure returns to phase 4, a violated NFR returns to phase 1. Roll back first, diagnose second. Every production failure adds an executable regression test using the project's grouping convention; without one, use a clearly named Gherkin-like native test linked to the incident. Failure paths are in `references/phase-5-release.md`.

## Working with the user

- Inspect before asking. Raise only consequential unresolved product, scope, architecture, UX, cost, or risk choices in the selected tracker.
- Keep gate requests short: what was decided, what you need approved, what happens next. Put non-`Done` ledger rows first; do not ask for approval while a required row is missing. A phase-exit review is not a second “please approve this phase” message.
- If the user says "just write the code" for a bounded non-trivial change, route to `compressed-small-change`; do not disguise a full feature or contract decision as compressed work.
- If work resumes in a new session, open the selected tracker item, feature index, and the latest phase ledger before touching anything. Re-show any still non-`Done` rows before continuing.
