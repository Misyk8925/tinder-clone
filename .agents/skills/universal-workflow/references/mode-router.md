# Mode router

Choose one mode before creating workflow artifacts. Route by the nature of the requested delivery, not by the number of files it may touch. Then resolve `references/project-profile.md` and use the mode-sized search in `references/efficient-project-search.md`.

## `full-feature-delivery`

Use for a new end-to-end capability, service, endpoint, integration, architecture or data-boundary change, or work that needs several observable slices and production delivery.

Run phases 1-5. Phase 1 has its concept approval. Phases 2 and 3 form one design package: draft the technical contracts in phase 2, add executable behavioural contracts in phase 3, run the combined consistency check, then request one approval for both.

Use the full feature artifact index and the project's selected tracker.

## `bug-fix`

Use when the implementation violates already agreed or clearly established behaviour and the requested outcome includes a fix. If the desired behaviour itself is undecided or changing, route to a change mode rather than labeling the decision a bug.

1. Reproduce the problem, or state why reproduction is blocked. Record expected versus actual behaviour, affected scope, and severity. Reproducible evidence of a violation confirms the bug; a symptom or plausible guess remains a lead.
2. Diagnose the root-cause mechanism, then inspect the existing requirement and public/data contracts. Update phase-2 artifacts only when the intended boundary changes or the old contract is itself wrong.
3. Add an executable regression check at the closest meaningful boundary. Follow the project's test convention; when none exists, use a Gherkin-like Given/When/Then test in the native framework. It must fail on the broken behaviour and pass on the fix.
4. Implement the smallest safe fix. Select additional test levels from the changed risks, then run targeted review of the fix and its neighbouring failure paths.
5. Run only the release, rollback, and monitoring checks affected by the fix. Report infrastructure-dependent checks as blocked, not passed.

Track this as one bug item in the selected tracker. Do not require concept documents, milestones, a full feature tree, or unrelated contract/test artifacts. Ask for approval before coding only when intended behaviour, a contract change, or risk acceptance needs an owner decision.

## `compressed-small-change`

Use for one bounded, low-risk, reversible change with clear expected behaviour that fits one observable slice. Examples include a small validation rule, local refactor with behavioural evidence, minor UI behaviour, or mechanical configuration change that is not trivial enough to do without verification.

Create one compact issue or plan entry containing:

- intended outcome and out of scope;
- affected contract, or `none`;
- Gherkin-like native acceptance check for changed behaviour, or other observable evidence for a mechanical, configuration, documentation, or visual-only change;
- selected test levels and exact validation commands;
- rollback note when the change can affect runtime behaviour.

Implement one slice, run the applicable checks, and perform one targeted review. Do not create concept documents, a feature/project tracking container, phase milestones, or empty phase folders. Reuse existing repo locations for tests and contracts instead of duplicating them under `docs/features/`.

Promote this mode to `full-feature-delivery` when discovery reveals a consequential owner decision, incompatible or cross-consumer contract change, multi-step or irreversible migration, security/privacy policy change, architecture decision, irreversible operational change, or more than one coherent slice. Merely touching a contract or migration is not enough: a small additive backward-compatible contract change or rollback-safe migration may remain compressed when it is low-risk, fits one coherent slice, needs no unresolved owner decision, and has deterministic compatibility, migration, and rollback evidence. Promote it to `bug-fix` when the task is actually correcting existing behaviour.

## Outside this skill

Do not use this skill for read-only review or audit, standalone RCA without a requested fix, explanation, research, pure specification/planning, release-only operation, or a trivial edit. Follow a more specific skill or the project's normal guidance instead, unless the user explicitly asks to apply this workflow.
