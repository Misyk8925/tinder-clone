# Contract conventions: <project name>

**Destination:** `docs/contracts/conventions.md` at the project root — a sibling of `docs/features/`, not inside any single feature's folder. Written once by whichever feature needs it first; every later feature reads it, never copies or re-decides it.

Project-wide. Written once, referenced by every feature — do not re-decide this per feature.

| Connection type | Format | Linter / validator | Notes |
|---|---|---|---|
| HTTP API | OpenAPI 3.1 | <e.g. Spectral> | |
| Events (queue/broker) | AsyncAPI 2.x | | Broker: <Kafka / RabbitMQ / ...> |
| Websockets | AsyncAPI 2.x | | |
| Database migrations | <tool, e.g. Flyway / golang-migrate / Prisma> | | |

## Mirror generation

- Tool used to turn spec → markdown mirror: <widdershins / redoc-cli / hand-written / ...>
- Command: `<...>`
- If hand-written: mirror is checked against the spec as part of the phase-2 gate checklist, every time.

## Naming

- Error code style: `SCREAMING_SNAKE_CASE`
- Event name style: `<domain>.<entity>.<verb-past-tense>.v<n>` e.g. `quote.draft.created.v1`

## Bug-hunt audit cadence

Full-codebase audits (`references/bug-hunt.md`) are never automatic — decide once when to run them:

- Cadence: <e.g. "before every production release" | "every 10 completed features">
- Standing tracker: Linear "Bug Audits" project (`<link>`), or `docs/bug-audits/` if no Linear.

## History

| Date | Change |
|------|--------|
