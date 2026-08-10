# Behaviour traceability — <feature-slug>

Acceptance format: <project convention | Gherkin-like native default and runner>

Validation command: `<command>` · status: <red for expected missing behaviour | pass | blocked> · last checked <date>

## FR / contract-error → executable acceptance check

| FR or error | Test/scenario ID | File |
|---|---|---|
| FR-1 | | |
| FR-2 | | |
| 400 INVALID_PAYLOAD | | |
| 409 ... | | |

A blank row is either a missed acceptance check or a requirement that turned out not to need one — resolve which before asking for the combined phase-2/3 approval.

## Contract and acceptance validation notes

<spec/mirror drift, lint/parse failures, undefined Gherkin steps, native test discovery failures, or blocked environments — one line each>
