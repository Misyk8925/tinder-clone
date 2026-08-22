#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

root = File.expand_path("..", __dir__)
compose = YAML.safe_load(File.read(File.join(root, "docker-compose.yml")), aliases: true)
services = compose.fetch("services")

expected_identities = {
  "profiles" => "profiles-service.p12",
  "deck" => "deck-service.p12",
  "deck-read-api" => "deck-read-service.p12",
  "deck-read-worker" => "deck-read-service.p12",
  "consumer" => "consumer-service.p12",
  "subscriptions" => "subscriptions-service.p12"
}.freeze

failures = []
expected_identities.each do |service_name, identity|
  expected = [identity, "truststore.jks"]
  volumes = Array(services.fetch(service_name)["volumes"])

  expected.each do |filename|
    target = "/certs/#{filename}"
    mount = volumes.find { |candidate| candidate.is_a?(Hash) && candidate["target"] == target }
    expected_source = "/etc/dokploy/certs/tinderclone/#{filename}"

    if mount.nil?
      failures << "#{service_name} must bind #{target}"
    elsif mount["type"] != "bind" || mount["source"] != expected_source
      failures << "#{service_name} must bind #{expected_source} to #{target}"
    elsif mount["read_only"] != true
      failures << "#{service_name} mount #{target} must be read-only"
    elsif mount.fetch("bind", {})["create_host_path"] != false
      failures << "#{service_name} mount #{target} must set bind.create_host_path=false"
    end
  end
end

unless failures.empty?
  warn failures.map { |failure| "FAIL: #{failure}" }.join("\n")
  exit 1
end

puts "Compose mTLS mount policy: OK"
