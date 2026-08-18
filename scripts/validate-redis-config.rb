#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

root = File.expand_path("..", __dir__)
compose = YAML.safe_load(File.read(File.join(root, "docker-compose.yml")), aliases: true)
redis = compose.fetch("services").fetch("redis")
command = redis.fetch("command").to_s
volumes = Array(redis["volumes"])
declared_volumes = compose.fetch("volumes", {})
deck_environment = compose.fetch("services").fetch("deck").fetch("environment")

failures = []
failures << "legacy Redis must enable AOF persistence" unless command.include?("--appendonly yes")
failures << "legacy Redis must fsync AOF at least every second" unless command.include?("--appendfsync everysec")
failures << "legacy Redis must fail writes instead of silently evicting coordination/cache-index keys" unless command.include?("--maxmemory-policy noeviction")
failures << "legacy Redis must mount /data" unless volumes.include?("redis-data:/data")
failures << "legacy Redis data volume must be declared" unless declared_volumes.key?("redis-data")
failures << "Deck must not use the ignored SPRING_REDIS_TIMEOUT property" if deck_environment.key?("SPRING_REDIS_TIMEOUT")
failures << "Deck must configure the Spring Data Redis command timeout" unless deck_environment.key?("SPRING_DATA_REDIS_TIMEOUT")
failures << "Deck must configure the Spring Data Redis connect timeout" unless deck_environment.key?("SPRING_DATA_REDIS_CONNECT_TIMEOUT")

unless failures.empty?
  warn failures.map { |failure| "FAIL: #{failure}" }.join("\n")
  exit 1
end

puts "Redis Compose policy: OK"
