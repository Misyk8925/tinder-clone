#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

ROOT = File.expand_path("..", __dir__)
HTTP_YAML = File.join(ROOT, "docs/contracts/http/deck-read.openapi.yaml")
HTTP_MD = File.join(ROOT, "docs/contracts/http/deck-read.openapi.md")
BACKFILL_HTTP_YAML = File.join(ROOT, "docs/contracts/http/profiles-deck-card-backfill.openapi.yaml")
BACKFILL_HTTP_MD = File.join(ROOT, "docs/contracts/http/profiles-deck-card-backfill.openapi.md")
EVENT_YAML = File.join(ROOT, "docs/contracts/events/deck-read.asyncapi.yaml")
EVENT_MD = File.join(ROOT, "docs/contracts/events/deck-read.asyncapi.md")
BACKFILL_MD = File.join(ROOT, "docs/contracts/data/deck-card-backfill.md")
CONCEPT = File.join(ROOT, "docs/features/deck-read-cqrs/01-concept.ru.md")
DECK_READ_CONFIG = File.join(ROOT, "services/deck-read/src/main/resources/application.properties")
PROFILES_CONFIG = File.join(ROOT, "services/profiles/src/main/resources/application.yml")
COMPOSE = File.join(ROOT, "docker-compose.yml")
DEV_COMPOSE = File.join(ROOT, "docker-compose.deck-read-dev.yml")
RECOVERY_RUNBOOK = File.join(ROOT, "docs/features/deck-read-cqrs/04-implementation/recovery-runbook.md")
MIGRATION_SQL = File.join(ROOT, "migrations/migration/V2_profiles_deck_read_cqrs.sql")

def load_yaml(path, aliases: false)
  YAML.safe_load(File.read(path), permitted_classes: [], aliases: aliases)
rescue Psych::SyntaxError => e
  abort "YAML parse failed for #{path}: #{e.message}"
end

def assert(condition, message)
  abort "contract validation failed: #{message}" unless condition
end

http = load_yaml(HTTP_YAML)
events = load_yaml(EVENT_YAML)
backfill_http = load_yaml(BACKFILL_HTTP_YAML)
http_md = File.read(HTTP_MD)
backfill_http_md = File.read(BACKFILL_HTTP_MD)
event_md = File.read(EVENT_MD)
backfill_md = File.read(BACKFILL_MD)
concept = File.read(CONCEPT)
deck_read_config = File.read(DECK_READ_CONFIG)
profiles_config = load_yaml(PROFILES_CONFIG)
compose = load_yaml(COMPOSE, aliases: true)
dev_compose = load_yaml(DEV_COMPOSE)
recovery_runbook = File.read(RECOVERY_RUNBOOK)
migration_sql = File.read(MIGRATION_SQL)

assert(http["openapi"] == "3.1.0", "HTTP canonical spec must be OpenAPI 3.1.0")
assert(events["asyncapi"] == "2.6.0", "event canonical spec must be AsyncAPI 2.6.0")
assert(backfill_http["openapi"] == "3.1.0", "Profiles maintenance spec must be OpenAPI 3.1.0")

backfill_path = backfill_http.dig("paths", "/api/v1/profiles/internal/deck-card-projection/backfills/{runId}")
assert(backfill_path&.key?("post") && backfill_path&.key?("get"), "start/resume/status backfill operations are incomplete")
assert(backfill_http.dig("components", "securitySchemes", "mutualTLS", "type") == "mutualTLS", "backfill endpoint must require mTLS")
backfill_statuses = backfill_http.dig("components", "schemas", "BackfillRun", "properties", "status", "enum")
assert(backfill_statuses == %w[RUNNING ENQUEUED COMPLETED FAILED], "backfill API status enum drifted")
%w[runId same POST COMPLETED FAILED ENQUEUED 500 outbox].each do |term|
  assert(backfill_http_md.include?(term), "backfill HTTP readable view misses #{term}")
end

v2 = http.dig("paths", "/api/v2/deck", "get")
v1 = http.dig("paths", "/api/v1/deck", "get")
assert(v2, "GET /api/v2/deck is missing")
assert(v1 && v1["deprecated"] == true, "deprecated GET /api/v1/deck is missing")
assert(v2.fetch("responses").keys.sort == %w[200 202 400 401 503], "v2 response set must be exactly 200/202/400/401/503")
assert(v1.fetch("responses").keys.sort == %w[200 400 401 503], "v1 response set must be exactly 200/400/401/503")

retry_after = v2.dig("responses", "202", "headers", "Retry-After", "schema", "const")
assert(retry_after == 2, "202 must require Retry-After: 2")

limit = v2.fetch("parameters").map { |p| p["$ref"] }.include?("#/components/parameters/Limit")
assert(limit, "v2 must use the canonical limit parameter")
assert(http.dig("components", "parameters", "Limit", "schema", "minimum") == 1, "limit minimum must be 1")
assert(http.dig("components", "parameters", "Limit", "schema", "maximum") == 100, "limit maximum must be 100")

states = http.dig("components", "schemas", "DeckPage", "properties", "state", "enum")
assert(states == %w[READY REFRESHING DEGRADED EMPTY], "DeckPage state enum drifted")
required_page = http.dig("components", "schemas", "DeckPage", "required")
assert(%w[items nextCursor generation cursorReset state].all? { |field| required_page.include?(field) }, "DeckPage required fields incomplete")

problem_required = http.dig("components", "schemas", "Problem", "required")
assert(%w[type title status code detail].all? { |field| problem_required.include?(field) }, "Problem must carry RFC 7807 fields plus code")

v1_items = v1.dig("responses", "200", "content", "application/json", "schema")
assert(v1_items["type"] == "array" && v1_items["items"]["$ref"] == "#/components/schemas/DeckCardV1", "v1 success must remain a bare array")

v2_photo_required = http.dig("components", "schemas", "Photo", "required")
assert(v2_photo_required == %w[photoId url order], "v2 photo shape drifted")
v1_photo_required = http.dig("components", "schemas", "PhotoV1", "required")
assert(v1_photo_required == %w[url position isPrimary], "v1 legacy photo fields must be preserved exactly")
v1_required = http.dig("components", "schemas", "DeckCardV1", "required")
assert(v1_required == %w[profileId name age city bio photos hobbies], "v1 legacy card shape drifted")
assert(!http.dig("components", "schemas", "DeckCardV1", "properties").key?("preferences"), "v1 must not gain v2 preferences")

expected_channels = ["profile.deck-card-projection.v1", "swipe-saved", "match.created", "deck.built.v1", "deck-read.materialization-requested.v1"]
assert(events.fetch("channels").keys.sort == expected_channels.sort, "event scope must contain projection, mutation, Deck-built and materialization topics")

profile_event_required = events.dig("components", "schemas", "ProfileDeckCardProjectionEvent", "required")
assert(%w[eventId profileId userId version occurredAt operation source card].all? { |field| profile_event_required.include?(field) }, "profile projection metadata/card/source is incomplete")
assert(events.dig("components", "schemas", "ProfileDeckCardProjectionEvent", "properties", "source", "enum") == %w[LIVE BACKFILL], "projection source enum drifted")
profile_card_required = events.dig("components", "schemas", "DeckCardProjection", "required")
assert(%w[profileId name age city bio isActive preferences photos hobbies].all? { |field| profile_card_required.include?(field) }, "profile card projection is not full")

swipe_required = events.dig("components", "schemas", "SwipeSavedEvent", "required")
assert(swipe_required == %w[eventId profile1Id profile2Id decision timestamp], "existing swipe-saved wire contract drifted")
match_required = events.dig("components", "schemas", "MatchCreatedEvent", "required")
assert(match_required == %w[eventId profile1Id profile2Id createdAt], "existing match.created wire contract drifted")

%w[INVALID_CURSOR INVALID_LIMIT INVALID_PAGINATION UNAUTHENTICATED READ_MODEL_NOT_READY DECK_TEMPORARILY_UNAVAILABLE].each do |code|
  assert(http_md.include?(code), "HTTP readable view misses #{code}")
end
expected_channels.each { |topic| assert(event_md.include?(topic), "event readable view misses #{topic}") }
%w[500 last_profile_id backfill_run_id RUNNING ENQUEUED COMPLETED FAILED].each do |term|
  assert(backfill_md.include?(term), "backfill data contract misses #{term}")
end
assert(event_md.include?("Backfill code never sends directly to Kafka"), "same-outbox backfill rule is missing")

forbidden = ["deck.build-requested", "deck.rebuilt", "deck.build-failed", "profile.discovery-projection", "swipe.decision.v2"]
forbidden.each do |name|
  assert(!EVENT_YAML.then { |path| File.read(path) }.include?(name), "out-of-scope event #{name} reappeared")
end

(1..9).each { |number| assert(concept.include?("FR-#{number}"), "concept misses FR-#{number}") }
assert(concept.include?("Profiles PostgreSQL — authoritative recovery source"), "authoritative recovery source is not explicit")
assert(concept.include?("`deck.built.v1`"), "Deck build notification boundary is not explicit")
assert(concept.include?("viewerUserId → viewerProfileId"), "userId/profileId viewer boundary is ambiguous")
assert(concept.include?("максимум по 500 строк"), "backfill page limit is not explicit")
assert(concept.include?("события страницы в существующий `profile_event_outbox`"), "same-outbox page transaction is not explicit")

assert(deck_read_config.include?("%dev.quarkus.redis.read-model.client-type=standalone"), "development read-model Redis must use the standalone client")
assert(deck_read_config.include?("DECK_READ_PROFILE_GROUP_ID"), "profile consumer group must be recovery-configurable")
assert(deck_read_config.include?("DECK_READ_SWIPE_GROUP_ID"), "swipe consumer group must be recovery-configurable")
assert(deck_read_config.include?("DECK_READ_MATCH_GROUP_ID"), "match consumer group must be recovery-configurable")

dev_redis = dev_compose.dig("services", "deck-read-redis-dev")
assert(dev_redis, "standalone Deck Read development Redis service is missing")
assert(dev_redis.fetch("ports").include?("127.0.0.1:6380:6379"), "development Redis must expose localhost:6380")
assert(dev_redis.fetch("command").include?("noeviction"), "development Redis must preserve read-model data under memory pressure")

migration = compose.dig("services", "profiles-migrations")
assert(migration, "existing Profiles volumes have no one-shot migration service")
assert(migration.fetch("entrypoint").include?("--file=/migrations/V2_profiles_deck_read_cqrs.sql"), "Profiles migration service does not execute V2")
assert(compose.dig("services", "profiles", "depends_on", "profiles-migrations", "condition") == "service_completed_successfully", "Profiles must wait for the V2 migration")
assert(compose.dig("services", "profiles", "environment", "SPRING_JPA_HIBERNATE_DDL_AUTO") == "validate", "Compose Profiles must validate rather than mutate schema")
deck_read_api = compose.dig("services", "deck-read-api")
deck_read_worker = compose.dig("services", "deck-read-worker")
assert(deck_read_api, "Deck Read API role is missing")
assert(deck_read_worker, "Deck Read worker role is missing")
api_profiles = deck_read_api.dig("environment", "QUARKUS_PROFILE").to_s.split(",")
worker_profiles = deck_read_worker.dig("environment", "QUARKUS_PROFILE").to_s.split(",")
assert(api_profiles.include?("prod") && api_profiles.include?("api"), "Deck Read API must activate prod and api profiles")
assert(worker_profiles.include?("prod") && worker_profiles.include?("worker"), "Deck Read worker must activate prod and worker profiles")
deck_read_environment = deck_read_worker.fetch("environment")
assert(deck_read_environment.key?("DECK_READ_PROFILE_GROUP_ID"), "Compose cannot override the profile recovery group")
assert(deck_read_environment.key?("DECK_READ_SWIPE_GROUP_ID"), "Compose cannot override the swipe recovery group")
assert(deck_read_environment.key?("DECK_READ_MATCH_GROUP_ID"), "Compose cannot override the match recovery group")
assert(deck_read_environment.key?("DECK_READ_DECK_BUILT_GROUP_ID"), "Compose cannot override the Deck-built group")
assert(deck_read_environment.key?("DECK_READ_MATERIALIZATION_GROUP_ID"), "Compose cannot override the materialization group")
assert(profiles_config.dig("spring", "jpa", "hibernate", "ddl-auto") == "validate", "Profiles default Hibernate mode must be validate")
assert(migration_sql.include?("ADD COLUMN IF NOT EXISTS"), "V2 column upgrade must be replay-safe")
assert(migration_sql.include?("CREATE TABLE IF NOT EXISTS"), "V2 table upgrade must be replay-safe")

%w[DECK_READ_SWIPE_GROUP_ID DECK_READ_MATCH_GROUP_ID auto.offset.reset=earliest].each do |term|
  assert(recovery_runbook.include?(term), "recovery runbook misses #{term}")
end
assert(recovery_runbook.downcase.include?("не переиспользовать group ids"), "recovery must forbid committed recovery group reuse")
assert(recovery_runbook.include?("Нулевой lag означает только"), "zero lag must not be presented as replay proof")

puts "Deck Read and Profiles maintenance OpenAPI/AsyncAPI contracts and readable mirrors are structurally consistent."
