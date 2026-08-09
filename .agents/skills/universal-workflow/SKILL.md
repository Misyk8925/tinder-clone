---
name: universal-workflow
description: Spec-driven, gated workflow for shipping a feature end-to-end, tracked in Linear — concept (bilingual EN/RU, approval), API/event contracts (approval), Gherkin behavioural contracts, implementation loop (plan/code/test/review/refactor/e2e), bug hunt (targeted diff review every slice; full-codebase audit on request/cadence, never automatic; needs repro steps, root cause, severity, regression test to close), release (build/scan/deploy/smoke/monitoring/docs). Approvals, risks, questions, decisions and bugs live in Linear via its MCP server; contracts, Gherkin, data catalog and plan stay in the repo. Triggers — new feature/service/microservice/endpoint/API/integration/project; build X, add feature Y, design and implement Z, ship/deploy this, go live, production release; spec, requirements, PRD, design doc, RFC, ТЗ, техническое задание, техзадание; OpenAPI, AsyncAPI, webhook, event schema, FR/NFR, acceptance criteria, Cucumber, BDD, TDD, user stories, architecture design, regression tests, release checklist, rollback plan; code review, review my PR/diff; find bugs, bug hunt, bug audit, race condition, concurrency bug, flaky test, root cause analysis, postmortem, incident review, RCA; risk assessment, project risks, open questions, decisions log, sprint, epic, backlog, Linear; давай сделаем фичу, новый проект, новая фича, напиши спеку, ревью кода, найди баги, гонка потоков, план релиза, риски проекта, workflow, по нашему процессу. Prefer over ad-hoc coding for anything bigger than trivial.
---

# Universal Workflow

A gate-driven workflow for taking a feature from idea to production: five phases plus a bug-hunt gate. The point of the gates is simple: **code is the most expensive place to discover a misunderstanding**. Every phase produces an artifact the user reads and approves before the next phase starts.

Tracking lives in **Linear** — issues, projects, comments and documents already do what a hand-rolled state file was reimplementing badly: assignment, notification, search, history. The repo keeps only what's genuinely code-adjacent: contracts, Gherkin, the data catalog, the implementation plan. See `references/linear-integration.md` for the full mapping before touching either.

## Core rules

1. **Never skip ahead.** No API contracts before the concept is approved. No code before contracts and scenarios exist.
2. **Two hard gates** (phase 1 and phase 2) require an explicit "approved" from the user. Stop the turn and wait. Do not self-approve, do not assume silence means yes.
3. **Tracking lives in Linear, code-adjacent artifacts live in the repo.** The concept brief, approvals, risk register, open questions and decisions are Linear objects — reviewable, assignable, searchable, and visible to people who don't read git diffs. Contracts, Gherkin, the data catalog and the implementation plan are files, because they're read by tooling and CI, versioned alongside the code they describe, and diffed in the same PR. Neither substitutes for the other.
4. **Check for Linear before assuming it.** Not every project has it connected. If it isn't available, fall back to the pre-Linear, all-in-repo model — see "No Linear available" in `references/linear-integration.md`. Don't silently drop tracking either way.
5. **Failure moves backwards through the phases, never sideways.** A red test, a failed deploy or a production incident sends the work back to the phase that could have prevented it — and the fix includes a new test or scenario at that phase, so the same failure cannot recur.
6. **Risks are logged where they're found, not carried in your head.** Any phase can raise or close one — in Linear, a `risk` issue in the project; see `references/linear-integration.md` for the exact labels. Before phase 5 ships, every risk is Mitigated, Accepted (with an owner), or Closed — "Open" is not a state a release ships in. The goal isn't zero risk; it's that whatever remains is visible, deliberate, and owned by someone.
7. **The concept brief is bilingual** — English (simple, B1-level vocabulary, short sentences) and Russian, kept side by side. Later artifacts (contracts, code, scenarios) are English-only, since they are technical.
8. **A bug isn't real until it has repro steps and a root cause, and isn't fixed until a test fails on the old code and passes on the new one.** "Sometimes happens" is a lead, not a bug report — see `references/bug-hunt.md`. A full-codebase audit is never claimed as complete; report what this pass found, not what's true of the whole codebase.
9. **Once requirements are validated, design starts from the cheapest thing that still satisfies every FR and NFR — not the most impressive, scalable, or familiar one.** Cheapest in engineering time and operating cost both, never cheapest by quietly dropping a requirement. Every increment of cost or complexity past that baseline has to trace to a specific FR/NFR number; "best practice" and "we might need it later" don't count. This is what rejected alternatives are for.

## Where things live

**In Linear** (one Project per feature — or, for small features, one Issue with sub-issues; see `references/linear-integration.md`):

| Old file-based artifact | Linear object |
|---|---|
| `00-state.md` (phase, approvals) | Project milestones (one per phase) + a "Gate" issue per hard gate |
| `01-concept.en.md` / `.ru.md` | Two Project Documents attached to the Project |
| Risk register | Issues labeled `risk`, filtered in a saved Project view |
| Open questions | Issues labeled `question`, or unresolved comments |
| Decisions log | Project Updates (Linear's chronological status-update feed) |
| Incident tracking (live) | An issue labeled `incident`, linked to the postmortem file |
| Bug found (targeted review) | Issue, label `bug` + severity — fixed 4-field format, needs a regression test to reach Done (`references/bug-hunt.md`) |
| Full bug-audit run | Milestone in a standing "Bug Audits" Project, issues labeled `bug` + `bug-audit`, diffed against the previous run |

**In the repo.** Two different scopes — don't nest one inside the other:

```
docs/
├── contracts/
│   └── conventions.md          # PROJECT-ROOT, one per codebase — spec formats, naming, audit
│                                # cadence. Written once by whichever feature needs it first;
│                                # every later feature reads it, never re-decides or copies it.
├── quality/
│   └── metrics.md              # PROJECT-ROOT, per service/product stream — rolling SLO, delivery,
│                                # test, security, data, alert, and accessibility evidence
│   └── gates.md                # PROJECT-ROOT — deterministic CI quality gates and approved exceptions
├── bug-audits/                  # PROJECT-ROOT, fallback only (no Linear) — see references/bug-hunt.md
│   └── <date>-pass-<n>/
│       ├── report.md
│       └── <bug-slug>.md
└── features/
    └── <feature-slug>/          # everything below is per-feature, never shared
        ├── README.md            # entry point for agents: links the Linear project, indexes this folder
        ├── 02-contracts/
        │   ├── http/            # only if the feature has an HTTP API
        │   │   ├── openapi.yaml # canonical, machine-readable
        │   │   └── openapi.md   # full human/agent-readable mirror
        │   ├── events/           # only if the feature publishes/consumes events
        │   │   ├── asyncapi.yaml
        │   │   └── asyncapi.md
        │   ├── websockets/       # only if the feature has a ws channel
        │   │   ├── asyncapi.yaml
        │   │   └── asyncapi.md
        │   └── data/             # only if the feature changes the schema
        │       ├── migrations/   # pointer to, or copy of, the real migration files
        │       └── data-catalog.md
        ├── 03-behaviour/
        │   ├── README.md         # traceability table + dry-run notes
        │   └── *.feature         # Gherkin
        ├── 04-implementation/
        │   ├── plan.md           # task breakdown
        │   └── log.md            # iteration log: what failed, what changed
        │   └── qa-metrics.md     # evidence-backed quality snapshot, from phase 4 through release
        ├── bugs/                  # No Linear only — targeted-review bugs, see references/bug-hunt.md
        │   └── <bug-slug>.md
        └── 05-release/
            ├── checklist.md
            └── incidents/         # only if something went wrong
                └── <date>-<slug>.md
```

Only create the `02-contracts/` subfolders a feature actually needs. Within a subfolder that does exist, **both files are mandatory, never just one** — the spec is what tooling validates against, the mirror is what a human or an agent reads without parsing YAML.

## The phases

Read the matching reference file when you enter a phase — it contains the Linear objects to create, the templates, the questions to ask, and the exit criteria.

| # | Phase | Reference | Gate |
|---|-------|-----------|------|
| 1 | Concept | `references/phase-1-concept.md` | **Manual approve** |
| 2 | API & event contracts | `references/phase-2-contracts.md` | **Manual approve** |
| 3 | Behavioural contracts | `references/phase-3-behaviour.md` | Soft (confirm scenarios) |
| 4 | Implementation loop | `references/phase-4-implementation.md` | Loop until green |
| 4.5 | Bug hunt | `references/bug-hunt.md` | Targeted: every slice, part of Review. Full audit: on request / periodic — never automatic every session. |
| 5 | Release | `references/phase-5-release.md` | Ship |

Templates for repo artifacts are in `assets/templates/`; the Linear object mapping is in `references/linear-integration.md`.

### Phase 1 — Concept (gate)

Create a Linear Project for the feature (or an Issue with sub-issues, for small work) with a milestone per phase. Write the concept brief as two Project Documents (EN B1, RU), covering:

- **Problem** — who has it, when, what it costs them today. Concrete, not "users want a better experience".
- **Contracts and behaviour in plain text** — what goes in, what comes out, what the system promises, what it refuses to do. No JSON yet — prose that a non-engineer could check.
- **Functional requirements** — numbered FR-1, FR-2…, each testable.
- **Non-functional requirements** — numbered NFR-1…, each with a number attached (latency, throughput, availability, data retention, security, cost). "Fast" is not an NFR; "p95 < 300 ms at 50 rps" is.
- **Out of scope** — the cheapest section to write and the most expensive to omit.
- **Suggested solution** — the cheapest design that satisfies every FR/NFR exactly as validated, not the most impressive one: components, data flow, storage, external systems. Anything pricier than the obvious baseline needs to trace to a specific FR/NFR number, not "best practice" — and *at least one rejected alternative with the number it failed to meet*. A design with no rejected alternative was not a decision. Draw a diagram alongside the prose only once there's enough to draw — a single-service, single-flow design doesn't need one.

Interview against a category checklist (user, trigger/success, boundaries, numbers, integration, failure, stakeholders) rather than a fixed script — skip what's inferable, but say what you assumed instead of deciding it silently. Before showing the draft to the user, run it through a short self-check loop: check it against the existing project (stack, conventions, other Linear projects) and verify the specific claims worth verifying (feasibility, numbers, external limits) with a few targeted searches — capped at two passes, not an open-ended investigation. Findings become either an open question (issue labeled `question`) or a risk (issue labeled `risk`) — not silently folded into the draft as if settled. See `references/phase-1-concept.md`.

Then stop and ask for approval. Once given, close the phase-1 Gate issue and post a Project Update. Do not start phase 2 in the same turn.

### Phase 2 — API & event contracts (gate)

Turn the prose into contracts, and for every connection type the feature uses, produce **two files, not one**: a canonical machine-readable spec (OpenAPI for HTTP, AsyncAPI for events and for websockets) and a full markdown mirror. Same rule for the database: a real migration is the canonical schema change, `data-catalog.md` is its readable twin. The spec format per connection type is a project-wide convention decided once — check `docs/contracts/conventions.md` before picking one yourself. Include error and edge cases in both files — a contract that only describes the happy path is a wish, not a contract. Anything found that isn't fixable here and now becomes a `risk` issue in Linear. Stop and ask for approval; close the phase-2 Gate issue once given.

### Phase 3 — Behavioural contracts

Write Gherkin `.feature` files, one scenario per FR plus scenarios for the error paths from phase 2. These become the executable acceptance criteria; phase 4 is not done until they pass. Before showing the list to the user, run a mechanical check, not a second gate: cross-check traceability against the FR list and contract errors (don't just assert coverage), and dry-run the suite to catch undefined steps, ambiguous matches, or malformed tables. Confirm the scenario list with the user, then continue — this gate is soft.

### Phase 4 — Implementation loop

```
plan → code → unit/integration test → review → refactor → e2e
   ↑                                                       │
   └──────────── if anything fails, back to plan ──────────┘
```

Run this per slice, not per feature — a slice is the smallest thing that makes at least one scenario pass end-to-end. Log every failed iteration in `04-implementation/log.md`. Start `04-implementation/qa-metrics.md` from `assets/templates/qa-metrics.md` and update it with the actual test, traceability, defect, and NFR evidence as slices finish; see `references/qa-metrics.md`. A review finding that's a genuine defect is a bug (phase 4.5); a review finding that's a deliberate tradeoff is a `risk` issue in Linear directly — either way it's tracked, never a comment that disappears into chat history. Exit the loop only when every scenario from phase 3 passes and review found nothing blocking.

### Phase 4.5 — Bug hunt

Two modes, don't conflate them. **Targeted review** is scoped to a diff and runs twice — every slice as part of phase 4's Review step, and once more across the whole feature's diff when all slices are done (catches what two individually-fine slices break together, which no single slice's review can see). **Full audit** scans the whole codebase and runs only on explicit request or at a cadence decided once in `docs/contracts/conventions.md` — never automatically every session.

Every confirmed bug is a Linear issue in one fixed shape: repro steps (exact, not "sometimes happens"), root cause (mechanism, not symptom), severity (blocker/major/minor/cosmetic), affected scope. It can't reach Done without a regression test that fails on the pre-fix code and passes on the fix — and for race conditions, a test that actually forces the concurrent interleaving, not a serial happy-path test. A full audit additionally diffs against the previous run: new / closed / recurring, where recurring means either the earlier fix was wrong or this is a false positive — flagged for manual review either way, never auto-resolved. Never claim a full audit found "all" the bugs; report what this pass found. Full details in `references/bug-hunt.md`.

### Phase 5 — Release

Build → security scan → deploy → smoke tests → monitoring → docs. Each step gates the next: a failing scan stops the deploy. **Before shipping: the Linear project's `risk-open` view is empty, and there's no open `blocker`-severity bug in this feature's scope** — every risk is Mitigated, Accepted with an owner, or Closed, and every blocker is fixed or explicitly downgraded. Complete the release portion of `04-implementation/qa-metrics.md` only after its stated observation window; until then, distinguish “not yet observed” from “passed.”

**If a step fails, the loop goes backwards, and where to depends on what failed** — a build failure returns to phase 4, a violated NFR returns to phase 1 (the design was wrong, not the code). Roll back first, diagnose second; rollback costs a deploy, debugging in production costs users. Every production failure ends as a `@regression` scenario in phase 3, so it can only happen once. Failure paths, the rollback rules, the incident loop and the post-mortem are in `references/phase-5-release.md` under "When the release fails".

## Working with the user

- Ask before assuming. If a requirement is ambiguous, raise it as a Linear `question` issue and ask — one round of questions is cheaper than one round of rework.
- Keep gate requests short: what was decided, what you need approved, what happens next.
- If the user says "just write the code", say what you'd be skipping and offer a compressed version (a short concept + contracts in one pass, a single Linear Issue instead of a full Project) rather than dropping the process entirely. Small tasks deserve a small process, not no process.
- If work resumes in a new session, open the feature's Linear project (or `docs/features/<slug>/README.md`, which links it) before touching anything.
- A full bug-audit can be asked for at any time, independent of which feature is in flight — it's codebase-wide, not feature-scoped. Don't run one unprompted just because a session started; targeted review already covers the current diff.
