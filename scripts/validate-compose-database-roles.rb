#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

root = File.expand_path("..", __dir__)
compose = YAML.safe_load(File.read(File.join(root, "docker-compose.yml")), aliases: true)
init_sql = File.read(File.join(root, "docker/postgres/init-databases.sh"))

expected = {
  "consumer" => ["SPRING_DATASOURCE_USERNAME", "consumer_app"],
  "subscriptions" => ["SUBSCRIPTIONS_DB_USER", "subscriptions_app"],
  "swipes" => ["SPRING_DATASOURCE_USERNAME", "swipes_app"]
}.freeze

provisioned_roles = init_sql.scan(/CREATE ROLE\s+([a-z0-9_]+)/).flatten
failures = expected.each_with_object([]) do |(service_name, (environment_key, expected_role)), result|
  service = compose.fetch("services").fetch(service_name)
  actual_role = service.fetch("environment")[environment_key]

  if actual_role != expected_role
    result << "#{service_name}.#{environment_key} must be #{expected_role.inspect}, got #{actual_role.inspect}"
  elsif !provisioned_roles.include?(expected_role)
    result << "#{expected_role} is used by #{service_name} but is not created by init-databases.sh"
  end
end

unless failures.empty?
  warn failures.map { |failure| "FAIL: #{failure}" }.join("\n")
  exit 1
end

puts "Compose database role policy: OK"
