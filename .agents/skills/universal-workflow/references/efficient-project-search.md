# Efficient project search

Goal: establish the smallest sufficient set of project facts before making decisions or editing code. Search is question-driven retrieval, not a ritual of reading the repository.

## Use available adapters, never require them

Use only tools that are already configured, authorized for the repository, and exposed by the current harness. Do not install an MCP server, request credentials, upload a private codebase, or widen indexed scope merely to follow this workflow.

- **Local text and file search** — use `rg`, `rg --files`, and focused Git history for exact symbols, routes, errors, config keys, filenames, and literals. This is normally the cheapest first step.
- **Serena** — when available and activated for the correct project, use its symbol overview, symbol lookup, declarations/implementations, and referencing-symbol retrieval for semantic code navigation. It is especially useful after a likely symbol or file is known.
- **Augment Context Engine** — when available and authorized, use `codebase-retrieval` for concept-level, cross-service, cross-repository, or history-aware questions where exact vocabulary or ownership is not yet known.

Do not call every engine for every task. Choose the cheapest adapter that can answer the current question, and escalate only when the result leaves material uncertainty.

## Check scope and freshness first

Before trusting indexed retrieval, confirm what it represents:

- the active project/root and relevant language backend for Serena;
- local versus remote Augment indexing;
- whether the result covers the current checkout, only a default branch, or another repository;
- ignored/generated paths and any consumer repository outside the active root.

Augment local indexing can reflect the working directory, while remote indexing may represent selected repositories' default branches. Treat any mismatch with the active checkout as a lead to verify locally, not as current truth.

## Search ladder

1. **Name the questions.** Typical questions are: where is the current behaviour defined, what is the canonical contract, who consumes it, where is state persisted, which tests prove it, and what release path is affected?
2. **Start exact.** Use `rg`/file search for known identifiers, routes, error text, schema names, config keys, and neighbouring tests. Open only the smallest relevant spans.
3. **Follow symbols.** Use Serena when symbol relationships matter: inspect the containing structure, definition, implementations, callers/references, and directly related tests. Use text search for dynamic wiring, reflection, templates, SQL, YAML, and other relationships a language server may not resolve.
4. **Retrieve by meaning.** Use Augment when terminology is uncertain or the change crosses services/repositories. Ask one bounded question that names the intended outcome, likely boundary, and desired evidence such as file paths, symbols, consumers, contracts, or analogous implementations.
5. **Verify authoritative files.** Open the returned canonical code, specs, migrations, tests, configuration, and actual consumers in the current checkout. Semantic summaries and index results identify candidates; they do not replace source inspection.
6. **Stop when sufficient.** Stop broad discovery once the affected boundary, consumers, conventions, risks, and validation path are known well enough for the selected mode. Record unresolved material uncertainty instead of accumulating context.

## Scope by delivery mode

- **Full feature delivery:** map the existing end-to-end path, closest analogue, canonical contracts, direct consumers, persistence/event boundaries, tests, ownership, and release topology before proposing the concept.
- **Bug fix:** establish expected behaviour first, then locate the faithful reproduction boundary, failing path, recent relevant history, callers/consumers, root-cause mechanism, and closest regression-test level. Do not expand into an unrelated codebase audit.
- **Compressed small change:** find the nearest convention, direct consumers, affected contract or `none`, and exact validation command. Stop after the one-slice impact is proven; promote the mode if search reveals wider decisions or scope.

## Evidence discipline

- Report paths, symbols, contracts, and exact commands that support the conclusion; do not dump raw search output into the handoff.
- Separate inspected facts from inferences and unverified indexed results.
- Re-run the narrowest decisive search after edits when references, generated code, or consumers may have changed.
- Never infer safety from "no results" until the searched root, ignored paths, index freshness, and dynamic/non-code wiring have been considered.

## Adapter documentation

- [Augment Context Engine MCP](https://docs.augmentcode.com/context-services/mcp/overview)
- [Serena](https://github.com/oraios/serena)
