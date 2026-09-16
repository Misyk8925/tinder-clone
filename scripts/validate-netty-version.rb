#!/usr/bin/env ruby
# frozen_string_literal: true

# Trivy image scans fail on CRITICAL CVE-2026-75595 in netty-handler 4.1.136.Final.
# Spring Boot services override that library via <netty.version>. Keep the pin at or
# above the first fixed 4.1 release so the scan cannot regress to 4.1.136.

require "pathname"

MINIMUM = "4.1.137.Final"
ROOT = File.expand_path("..", __dir__)

def numeric_parts(version)
  match = version.to_s.match(/\A(\d+)\.(\d+)\.(\d+)/)
  abort("FAIL: cannot parse Netty version #{version.inspect}") unless match
  [match[1].to_i, match[2].to_i, match[3].to_i]
end

def older_than_minimum?(version)
  (numeric_parts(version) <=> numeric_parts(MINIMUM)) < 0
end

poms = Dir.glob(File.join(ROOT, "services", "**", "pom.xml")).sort
failures = []
pins = 0

poms.each do |path|
  text = File.read(path)
  text.scan(/<netty\.version>([^<]+)<\/netty\.version>/).each do |match|
    pins += 1
    version = match.first.strip
    next unless older_than_minimum?(version)

    rel = Pathname.new(path).relative_path_from(ROOT)
    failures << "#{rel} pins netty.version #{version}; minimum is #{MINIMUM} (CVE-2026-75595)"
  end
end

failures << "no <netty.version> pin found under services/" if pins.zero?

unless failures.empty?
  warn failures.map { |failure| "FAIL: #{failure}" }.join("\n")
  exit 1
end

puts "Netty pin policy: OK (#{pins} pins >= #{MINIMUM})"
