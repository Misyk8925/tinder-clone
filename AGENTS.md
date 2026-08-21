# Codex guidance

## Cloud tasks

- Treat Codex Cloud as a reproducible build, unit-test, and code-review environment.
- Do not start the full Docker Compose stack by default. It requires Kafka, Keycloak, Redis, and several databases, and is reserved for explicitly requested integration work.
- Never read, add, print, or require production `.env` files, credentials, tokens, or cloud access keys. Use fixture data and test configuration only.
- Keep agent internet access disabled unless a task explicitly requires a narrowly scoped exception.
- Work on the smallest affected service(s). State which commands were run and separate passed checks from infrastructure-dependent checks that could not run.

## Repository layout and validation

- Java services live in `services/*`; run the Maven wrapper from the affected service when it exists.
- `services/tinder-contracts` is a local Maven dependency. Install it with `mvn -B -DskipTests install` before resolving dependencies or building Deck, Deck Read, or Profiles.
- Go services are `services/location-go` and `services/swipes-go`; validate with `go test ./...` from the affected service.
- The photos service is `services/photos`; validate with `python -m pytest` after `pip install -r requirements-dev.txt`.
- The Angular client is `clients/tinder-client`; use `npm ci` for dependency installation and `npm run build` for a production build check.
- Some tests use Testcontainers, Kafka, Redis, PostgreSQL, or Quarkus Dev Services. Do not report those as passed when their required runtime was unavailable.
- Before changing a cross-service DTO or endpoint, inspect its actual consumers and preserve shared contracts unless the task explicitly changes the boundary.
