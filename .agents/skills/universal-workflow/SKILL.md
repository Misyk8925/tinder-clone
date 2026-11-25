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
7. **Write in simple technical English.** B1 grammar and everyday words; domain terms (webhook, JWT, idempotency) stay as they are. This is always the language of the concept. Other documents follow the project's established documentation language, else this same English. Add other language versions only when the project profile or user asks for them.
8. **Use one bug lifecycle:** a lead becomes `confirmed` when reproducible evidence shows actual behaviour violating established expected behaviour, `diagnosed` when the root-cause mechanism is understood, and `fixed` only when a faithful regression test fails on the broken code and passes on the fix. "Sometimes happens" remains a lead — see `references/targeted-defect-review.md`.
9. **Design starts from the cheapest thing that satisfies every FR and NFR** — in engineering time and operating cost, never by dropping a requirement. Anything pricier must trace to a specific FR/NFR ID; "best practice" and "we might need it later" don't count. Rejected alternatives show this.
10. **Agents establish facts; people own consequential choices.** Inspect code, contracts, consumers, and tracker first (`references/efficient-project-search.md`). Ask only about real product, scope, architecture, UX, cost, or risk choices, and record the answer.
11. **Expose decisions before declaring them.** In full feature delivery, show the decision tree (`Fact` / `Proposed` / `Open` / `Approved`) before any concept draft or chosen architecture. `Approved` needs an explicit owner answer or a cited prior decision. Run at least one frontier round while a consequential choice is open; if none is, say so and cite sources.
12. **Work in observable slices with durable hand-offs.** A slice fits one fresh implementation context and gets a separate fresh-context review. Hand off through the ticket, artifacts, code, and test evidence — not a transcript.
13. **Never silently skip a sub-step.** Every sub-step has a ledger row (`references/phase-ledger.md`). `N/A` needs a change-specific reason; `Blocked` is not passed; `Deferred` needs an owner agreement. A missing row is as bad as a skipped gate.
14. **Review each phase before leaving it, without extra gates** (`references/phase-exit-review.md`). Fold the review into the existing gate requests. Shorter modes get one exit review.

## Where things live

**In the selected tracker** (use `references/linear-integration.md` only for projects that use Linear):

| Workflow state | Tracker representation |
|---|---|
| Phase and approvals | Phase milestones/status plus one gate item per hard gate |
| Concept | Project document in simple technical English; extra language versions only on request |
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
| 2 | Interface contracts (any stack) | `references/phase-2-contracts.md` | Continue to phase 3 | Yes, no extra stop |
| 3 | Executable behavioural contracts | `references/phase-3-behaviour.md` | **Manual approve phases 2 + 3** | Folded into that gate |
| 4 | Implementation loop | `references/phase-4-implementation.md` | Loop until green | Per slice + combined diff |
| 5 | Release | `references/phase-5-release.md` | Ship | Ship-evidence check |

Templates in `assets/templates/` are short filled samples for `bio-max-length`. Copy the shape, replace the sample. Ledger tables show two or three rows only; the closed ID list stays in `references/phase-ledger.md`. Tracker and project conventions come from `references/project-profile.md`.

Phase essentials (details live in the reference files):

- **Phase 1** — the concept is a 2–4 sentence summary, an in-scope table, and an out-of-scope table, in simple technical English. Show the decision tree before choosing an approach. Stop for approval.
- **Phase 2** — contracts for every boundary the feature crosses (API, data, UI, state, config, jobs, model I/O, shared code) in canonical project locations, each with its consumer list, linked from the feature index. No approval stop; continue to phase 3.
- **Phase 3** — executable acceptance tests for every FR and error path (project convention, else Gherkin-like native tests). Request one approval for phases 2 + 3.
- **Phase 4** — per-slice loop: plan → red → code → green → fresh-context review → refactor; failures go back to plan. User-visible journeys are proven with recorded browser runs via the `e2e-recording` skill (`.agents/skills/e2e-recording/SKILL.md`), linked from the PR. AFK slices whose evidence runs in the cloud may be dispatched as one cloud task and PR each; HITL slices and gates stay with the owner.
- **Phase 5** — build → security scan → deploy → smoke → monitoring → docs. No Open risk and no blocker bug ships. Roll back first, diagnose second.

## Working with the user

- Inspect before asking. Raise only consequential unresolved product, scope, architecture, UX, cost, or risk choices in the selected tracker.
- Keep gate requests short: what was decided, what you need approved, what happens next. Put non-`Done` ledger rows first; do not ask for approval while a required row is missing. A phase-exit review is not a second “please approve this phase” message.
- If the user says "just write the code" for a bounded non-trivial change, route to `compressed-small-change`; do not disguise a full feature or contract decision as compressed work.
- If work resumes in a new session, open the selected tracker item, feature index, and the latest phase ledger before touching anything. Re-show any still non-`Done` rows before continuing.
