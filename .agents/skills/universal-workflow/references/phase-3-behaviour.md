# Phase 3 — Behavioural contracts (Gherkin / Cucumber)

Goal: make the acceptance criteria executable. After this phase, "is the feature done?" is answered by a test run, not by an opinion.

## What to produce

`03-behaviour/<area>.feature` files. One feature file per bounded area, not one giant file.

Coverage rule: **every FR gets at least one scenario, and every error row from the phase-2 contract table gets one too.** A feature file with only happy paths is a demo script.

## Writing scenarios

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
- Tag them: `@fr-3 @smoke @e2e` — phase 5 reuses the `@smoke` set.

## Traceability

Keep a small table at the top of `03-behaviour/README.md`, from `assets/templates/behaviour-readme.md`:

```
| FR   | Scenarios                                   |
|------|---------------------------------------------|
| FR-1 | quote.feature: "Draft quote is created..."   |
| FR-2 | extraction.feature: 3 scenarios             |
```

A missing row means either a forgotten scenario or a requirement nobody needs.

## Agentic check, before the list goes to the user

Not a second gate — the same idea as phase 1's self-check loop, but mechanical instead of exploratory. Phase 3 has nothing to research: everything these scenarios need to be consistent with — FRs, NFRs, the contract's error tables — is already fixed and approved. So this is verification, not investigation, and it doesn't need a pass cap the way phase 1's does.

**Traceability, actually checked, not just asserted.** Walk the FR list and every error row from `02-contracts/*/openapi.md` / `asyncapi.md` against the scenarios just written — a literal pass down both lists, not "I covered the important ones." Fill in `03-behaviour/README.md` from this pass. A blank cell is either a missed scenario or a requirement that turned out not to need one — decide which; don't leave it ambiguous.

**Dry-run the suite, don't just read it.** Wire up (or reuse) whatever Gherkin runner the project already has — Cucumber, Behave, SpecFlow — and run its dry-run/parse mode before anything else touches these files: `cucumber --dry-run`, `behave --dry-run`, or equivalent. This catches what a read-through misses because it's mechanical: undefined steps, a step definition ambiguously matching two patterns, a malformed `Scenario Outline` `Examples` table, duplicate scenario names silently shadowing each other. A `.feature` file that reads fine but doesn't parse isn't a spec — it's prose with `Given`/`When`/`Then` in front of it. If the project has no runner wired up yet, this is where the minimum scaffolding gets built — step definitions that raise "pending," not implemented ones; phase 4 needs that scaffolding anyway, this just confirms it's wired correctly before building on top of it.

**What a finding becomes.** A mechanical failure (undefined step, bad table) gets fixed in this pass — it's fast and has one right answer. Something that reveals a genuine gap upstream (an FR with no sensible scenario, a contract error nothing here checks) is a phase-3 finding like any other: fixed now, or a `risk`/`risk-open` issue if it truly can't be — see Exit criteria.

Then show the scenario list to the user and confirm, per the soft gate.

## Exit criteria

- All FRs and contract errors covered — cross-checked against the traceability table, not assumed.
- The suite dry-runs clean: no undefined steps, no ambiguous matches, no malformed tables.
- Scenarios are readable by the user — show them the list and confirm before writing step definitions.
- Step definitions may be stubbed (failing) at this point. That is correct: they are the red half of red-green-refactor.
- If writing scenarios exposes something that can't be verified automatically (a behaviour that depends on a manual process, a third-party UI, timing that can't be made deterministic), that's a risk, not a scenario to fake — raise a Linear issue labeled `risk`/`risk-open` instead of writing a test that doesn't actually test anything.
