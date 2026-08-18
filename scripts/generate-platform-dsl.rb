#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "yaml"

ROOT = File.expand_path("..", __dir__)
GENERATED = File.join(ROOT, "platform", "generated")
COMPOSE = File.join(ROOT, "docker-compose.yml")
KAFKA_JSON = File.join(GENERATED, "kafka-catalog.json")
REDIS_JSON = File.join(GENERATED, "redis-catalog.json")
MISSING_JSON = File.join(GENERATED, "missing-dsl.json")
MISSING_KT = File.join(GENERATED, "missing-dsl.stubs.kts")

COMPOSE_REDIS_TO_CATALOG = {
  "redis" => "shared-redis"
}.freeze

def load_json(path)
  JSON.parse(File.read(path))
rescue Errno::ENOENT
  abort "catalog export missing: #{path}"
rescue JSON::ParserError => e
  abort "invalid catalog JSON #{path}: #{e.message}"
end

def discovered_redis_stores(compose)
  services = compose.fetch("services")
  stores = {}
  cluster_nodes = []

  services.each do |name, spec|
    image = spec["image"].to_s
    command = Array(spec["command"]).join(" ")
    next unless image.include?("redis")
    next unless command.include?("redis-server")

    if name.start_with?("deck-read-redis-")
      cluster_nodes << name
      next
    end

    stores[COMPOSE_REDIS_TO_CATALOG.fetch(name, name)] = {
      "composeServices" => [name],
      "topology" => command.include?("cluster-enabled") ? "CLUSTER" : "SINGLE_NODE"
    }
  end

  unless cluster_nodes.empty?
    stores["deck-read-cluster"] = {
      "composeServices" => cluster_nodes.sort,
      "topology" => "CLUSTER"
    }
  end

  stores
end

def discovered_kafka_topics(compose_text)
  compose_text.scan(/--topic\s+(\S+)/).flatten.uniq.sort
end

compose = YAML.safe_load(File.read(COMPOSE), aliases: true)
kafka_catalog = load_json(KAFKA_JSON)
redis_catalog = load_json(REDIS_JSON)

catalog_topics = Array(kafka_catalog["topics"]).map { |topic| topic["name"] }
catalog_stores = Array(redis_catalog["stores"]).map { |store| store["name"] }

runtime_topics = discovered_kafka_topics(File.read(COMPOSE))
runtime_stores = discovered_redis_stores(compose)

missing_topics = runtime_topics - catalog_topics
missing_stores = runtime_stores.keys - catalog_stores

Dir.mkdir(GENERATED) unless Dir.exist?(GENERATED)

missing = {
  "kafkaTopics" => missing_topics.map { |name| { "name" => name, "source" => "docker-compose.yml" } },
  "redisStores" => missing_stores.map do |name|
    runtime_stores[name].merge("name" => name, "source" => "docker-compose.yml")
  end
}

stubs = +""
missing_topics.each do |name|
  stubs << <<~KT
    topic(#{name.inspect}) {
        owner = "unknown"
        criticality = Criticality.REBUILDABLE
        messageKey = "TODO"
        producer("unknown", "ci-generated") { publishGuarantee = PublishGuarantee.BROKER_ACK }
        runtimeSource("docker-compose.yml")
    }

  KT
end
missing_stores.each do |name|
  topology = runtime_stores[name]["topology"]
  stubs << <<~KT
    store(#{name.inspect}) {
        owner = "unknown"
        role = StoreRole.REBUILDABLE_CACHE_AND_COORDINATION
        topology = RedisTopology.#{topology}
        persistence {
            mode = PersistenceMode.AOF
            appendFsync = AppendFsync.EVERY_SECOND
            dataVolume = "TODO:/data"
        }
        memory {
            maxMemoryBytes = 2L * 1024 * 1024 * 1024
            evictionPolicy = EvictionPolicy.NO_EVICTION
        }
        security { tls = false; authentication = false }
        risk(OperationalRisk.NO_TRANSPORT_OR_CLIENT_AUTH)
        risk(OperationalRisk.NO_EVICTION_REQUIRES_CAPACITY_ALERTS)
        runtimeSource("docker-compose.yml")
    }

  KT
end

File.write(MISSING_JSON, JSON.pretty_generate(missing))
File.write(MISSING_KT, stubs.empty? ? "// no missing platform DSL\n" : stubs)

if missing_topics.empty? && missing_stores.empty?
  puts "Platform DSL coverage: OK"
  puts "Kafka topics in catalog: #{catalog_topics.sort.join(', ')}"
  puts "Redis stores in catalog: #{catalog_stores.sort.join(', ')}"
  exit 0
end

warn "Missing Kafka topics: #{missing_topics.join(', ')}" unless missing_topics.empty?
warn "Missing Redis stores: #{missing_stores.join(', ')}" unless missing_stores.empty?
warn "Generated stubs: #{MISSING_KT}"
exit 1
