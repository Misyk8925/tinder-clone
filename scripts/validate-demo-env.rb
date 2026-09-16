#!/usr/bin/env ruby
# frozen_string_literal: true

# Given docker-compose.yml requires ${VAR:?...}, when .env.demo and
# docker-compose.demo.yml are read, then every required var is present and
# the demo overlay does not need real Stripe or S3.

require "yaml"

root = File.expand_path("..", __dir__)
compose_text = File.read(File.join(root, "docker-compose.yml"))
demo_env_path = File.join(root, ".env.demo")
demo_overlay_path = File.join(root, "docker-compose.demo.yml")
demo_up_path = File.join(root, "scripts/demo-up.sh")

failures = []

required = compose_text.scan(/\$\{([A-Z][A-Z0-9_]*):\?/).flatten.uniq.sort
env_keys = {}
File.readlines(demo_env_path, chomp: true).each do |line|
  stripped = line.strip
  next if stripped.empty? || stripped.start_with?("#")
  key, value = stripped.split("=", 2)
  failures << ".env.demo line is not KEY=value: #{line.inspect}" if key.nil? || value.nil?
  env_keys[key] = value
end

required.each do |key|
  value = env_keys[key]
  if value.nil? || value.empty?
    failures << ".env.demo missing required #{key} (compose uses ${#{key}:?})"
  elsif value.include?("replace-with")
    failures << ".env.demo #{key} is still a replace-with placeholder"
  end
end

overlay = YAML.safe_load(File.read(demo_overlay_path), aliases: true)
photos_bucket = overlay.dig("services", "photos", "environment", "AWS_S3_BUCKET")
unless photos_bucket == ""
  failures << "docker-compose.demo.yml photos.AWS_S3_BUCKET must be empty so MemoryStorage is used"
end

stripe_key = overlay.dig("services", "subscriptions", "environment", "STRIPE_SECRET_KEY")
unless stripe_key == "placeholder"
  failures << "docker-compose.demo.yml subscriptions.STRIPE_SECRET_KEY must be placeholder"
end

%w[STRIPE_WEBHOOK_SECRET STRIPE_PRICE_ID].each do |key|
  actual = overlay.dig("services", "subscriptions", "environment", key)
  unless actual == "placeholder"
    failures << "docker-compose.demo.yml subscriptions.#{key} must be placeholder"
  end
end

admin_hash = overlay.dig("services", "moderation", "environment", "MODERATION_ADMIN_PASSWORD_HASH")
client_hash = overlay.dig("services", "moderation", "environment", "MODERATION_CLIENT_PASSWORD_HASH")
[admin_hash, client_hash].each do |hash|
  # Compose YAML stores $$ so interpolation yields a BCrypt $2a/$2b hash.
  unless hash.is_a?(String) && hash.match?(/\A\$+2[abxy]?\$/)
    failures << "docker-compose.demo.yml must set BCrypt hashes for moderation (got #{hash.inspect})"
  end
end

unless File.file?(demo_up_path)
  failures << "scripts/demo-up.sh is missing"
end

unless failures.empty?
  warn failures.map { |failure| "FAIL: #{failure}" }.join("\n")
  exit 1
end

puts "Demo env policy: OK (#{required.length} required Compose vars present in .env.demo)"
