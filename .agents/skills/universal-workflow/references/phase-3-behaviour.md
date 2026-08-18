# Phase 3 — Executable behavioural contracts

Goal: make the acceptance criteria executable. After this phase, "is the feature done?" is answered by a test run, not by an opinion.

Show the phase-3 ledger from `references/phase-ledger.md` with the combined approval request. A missing FR, error-path check, or combined-check row is a protocol failure, not an implied skip.

## Choose the project's acceptance format

BDD means specifying observable behaviour before implementation; it does not require Gherkin syntax.

- Follow an established project convention, whether it uses `.feature` files, native BDD tests, or another executable acceptance style.
- If no convention exists, default to the native test framework with Gherkin-like Given/When/Then structure, domain-language names, and arrange/act/assert implementation. Do not add Cucumber solely to satisfy this workflow.
- Keep tests in the project's normal test tree and link their exact file and test names from `03-behaviour/README.md`; never duplicate test code under `docs/`.

`03-behaviour/README.md` is always produced for full feature delivery. It records the selected format, commands, traceability, and validation result.

Coverage rule: **every FR gets at least one executable acceptance check, and every error row from the phase-2 contract table gets one too.** Happy paths alone are a demo, not acceptance coverage.

## Gherkin syntax when the project uses it

Write from the point of view of the actor, in domain language. No selectors, no HTTP verbs, no SQL — those belong in the step definitions.

```gherkin
Feature: Quote generation from an inquiry

  Background:
    Given a tenant "Elektro Huber" with an active subscription

  Scenario: Draft quote is created from an email inquiry
    Given an inquiry arrives by email with the text "Steckdose kaputt, bitte Angebot"
    When the system processes the inquiry
    Then a draft quote is created for "Elektro Huber"
    And the quote status is "DRAFT"
    And the owner receives a notification

  Scenario: A quote that was already sent cannot be edited
    Given a quote in status "SENT"
    When the owner tries to change the price
    Then the change is rejected with error "QUOTE_ALREADY_SENT"
    And the quote price is unchanged

  Scenario Outline: Invalid inquiries are rejected
    Given an inquiry with <field> missing
    When the system processes the inquiry
    Then the inquiry is rejected with error "INVALID_PAYLOAD"

    Examples:
      | field    |
      | customer |
      | channel  |
```

Guidelines that keep these useful:

- **Declarative, not imperative.** "The owner sends the quote", not "the owner clicks the button with id #send".
- One behaviour per scenario. If a scenario has two `When`s, it is two scenarios.
- Deterministic. No "eventually", no reliance on wall-clock time — inject the clock.
- `Background` only for setup shared by *every* scenario in the file.
- Tag them by purpose, for example `@fr-3`, `@smoke`, or `@e2e`. Do not label every acceptance check as E2E; phase 5 reuses only the risk-selected smoke set.

## Native-framework convention

Write tests at the closest boundary where a stakeholder-observable outcome can be checked without coupling to implementation details. Name them in domain language and structure them as arrange/act/assert or given/when/then according to the project's style. Tag or group them as acceptance/smoke when the framework supports it.

Native acceptance tests follow the same rules as Gherkin scenarios: one behaviour per test, deterministic setup, observable outcomes, explicit error paths, and traceability to an FR or contract error. They are not required to be browser-level or full-system tests; the meaningful acceptance boundary may be an API, service component, message consumer, CLI, or UI.

## Traceability

Keep a small table at the top of `03-behaviour/README.md`, from `assets/templates/behaviour-readme.md`:

```
| FR   | Executable acceptance checks                |
|------|---------------------------------------------|
| FR-1 | quote.feature: "Draft quote is created..."   |
| FR-2 | extraction.feature: 3 scenarios             |
```

A missing row means either a forgotten acceptance check or a requirement nobody needs.

## Combined phase-2 + phase-3 check

Run one verification pass after phase 3, before asking for approval. This is the only approval check for phases 2 and 3; phase 2 is still a draft until its behaviour can be reviewed beside it.

**Validate the technical contracts.** Parse/lint canonical specs, verify any separately maintained readable view matches the changed surface, and check that auth, errors, edge cases, compatibility, and contract-relevant NFRs are explicit.

**Check traceability in both directions.** Walk every FR and every contract error row against the executable acceptance checks, then walk each endpoint/event/schema change and acceptance check back to an FR. Fill in `03-behaviour/README.md` from this literal pass, not from an assertion that the important cases are covered.

**Validate executability in the selected format.** For Gherkin, use the project's dry-run/parse mode to catch undefined or ambiguous steps, malformed tables, and duplicate scenarios. For native tests, run test discovery/compilation and the selected acceptance tests. Confirm that at least the first implementation slice's check is red for the expected missing behaviour, not because the harness, fixture, or environment is broken.

**Resolve findings at their source.** Fix malformed tests and contract/readable-view drift directly. Return to phase 1 for a requirement/design gap. Revise phase 2 for a contract gap. Record unresolved external or operational uncertainty in the selected tracker.

Then show the user one compact package: material contract decisions and compatibility impact, the FR/error-to-acceptance traceability list, validation commands/results, open risks, the phase-2 plus phase-3 ledgers, and the combined phase-exit review. Ask for one explicit approval covering phases 2 and 3. Do not send a separate “review session” message after that package. Do not begin phase 4 before approval.

## Exit criteria

- Canonical contracts parse/lint, any separate readable views match, and compatibility impact is explicit.
- All FRs and contract errors map to executable acceptance checks, and every contract/test maps back to an FR.
- Gherkin dry-runs clean when it is the convention; otherwise native acceptance tests are discovered/compiled and runnable.
- At least the next slice's acceptance check fails for the expected missing behaviour. Pending Gherkin steps are acceptable only when the project uses that convention.
- Behaviour that cannot be verified automatically is recorded as manual evidence or an owned risk, never represented by a fake automated test.
- The user explicitly approved the combined phase-2 and phase-3 package; the shared gate item records the approval.
- The phase-2 and phase-3 ledgers are complete; unused contract types and unautomatable behaviour are explicit `N/A` or owned-risk rows, not missing.
- The combined phase-exit review is in the same gate request, not a later extra stop.
