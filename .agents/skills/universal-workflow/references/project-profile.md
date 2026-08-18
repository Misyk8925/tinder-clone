# Project profile

Resolve project policy after choosing the router mode and before creating artifacts. This keeps the workflow portable while allowing each repository to keep its own tracker, language, contract, test, and release conventions.

## Resolution order

1. Repository instructions such as `AGENTS.md`, contributor guides, and architecture/testing documentation.
2. An explicit workflow profile or established neighbouring feature artifacts.
3. Existing executable conventions: test runners, canonical specs, migration locations, CI commands, release tooling, and tracker usage.
4. The portable defaults below.

A higher item wins. Record a consequential missing convention once; do not re-decide it per feature.

## Portable defaults

- **Tracker:** use the project's existing tracker. Use Linear only when the project already uses it and the integration is available. Otherwise use a compact repo-local state record for full features and an existing issue/plan entry for shorter modes.
- **Language:** use the project's documentation language. If none is established, use concise English. Add translated versions only when requested.
- **Acceptance tests:** follow the project's established style. If none exists, use the native test framework with Gherkin-like Given/When/Then structure and domain-language names. Do not add Cucumber only to satisfy this workflow.
- **Project search:** use local exact search by default. Use already configured semantic or symbol-aware adapters such as Augment Context Engine or Serena when they reduce material uncertainty; do not make them prerequisites or trust an index without checking its root and freshness. Follow `references/efficient-project-search.md`.
- **Contracts:** update the canonical project spec, schema, and migration locations. If none exists, default HTTP to OpenAPI 3.1 and events/websockets to AsyncAPI, plus a generated or maintained readable view. Feature artifacts link canonical files; they do not copy them.
- **Evidence:** use existing CI reports and test locations. Create workflow-specific evidence files only for full feature delivery or when the project requires them.
- **Release:** build and validate the affected scope. Deploy, message external users, or mutate production only when explicitly authorized and supported by project process.

## Profile fields worth recording

- tracker and approval mechanism;
- documentation language(s);
- feature index location;
- canonical HTTP/event/data contract locations and validation commands;
- acceptance-test framework, naming/grouping convention, and smoke/regression selection;
- available search/navigation adapters, indexed roots, and freshness limitations;
- build, integration, security, migration, and release commands;
- environments that may be used and who authorizes deployment.

Do not create a profile file merely to restate obvious repository facts. Create one when several features would otherwise repeatedly infer the same consequential choices.
