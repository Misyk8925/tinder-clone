#!/usr/bin/env ruby
# frozen_string_literal: true

# Trivy writes SARIF for code scanning, which leaves the job log with no trace
# of what actually failed. This renders the same SARIF as a readable table so a
# failing scan says which package and CVE to fix.

require "json"

path = ARGV[0]
abort("usage: report-trivy-sarif.rb <sarif-file>") if path.nil?

unless File.exist?(path)
  warn "No SARIF report at #{path}; the scan did not produce one."
  exit 0
end

report = JSON.parse(File.read(path))
run = Array(report["runs"]).first || {}
rules = Array(run.dig("tool", "driver", "rules")).each_with_object({}) do |rule, index|
  index[rule["id"]] = rule
end

FIELDS = {
  "package" => /^Package:\s*(.+)$/,
  "installed" => /^Installed Version:\s*(.+)$/,
  "fixed" => /^Fixed Version:\s*(.+)$/,
  "severity" => /^Severity:\s*(.+)$/
}.freeze

findings = Array(run["results"]).map do |result|
  text = result.dig("message", "text").to_s
  parsed = FIELDS.transform_values { |pattern| text[pattern, 1]&.strip }
  target = result.dig("locations", 0, "physicalLocation", "artifactLocation", "uri")
  rule = rules[result["ruleId"]] || {}

  {
    id: result["ruleId"].to_s,
    severity: parsed["severity"] || rule.dig("properties", "tags")&.find { |tag| tag =~ /\A(CRITICAL|HIGH|MEDIUM|LOW)\z/ } || "UNKNOWN",
    package: parsed["package"] || "-",
    installed: parsed["installed"] || "-",
    fixed: parsed["fixed"] || "-",
    target: target || "-"
  }
end

if findings.empty?
  puts "Trivy findings in #{path}: none."
  exit 0
end

order = { "CRITICAL" => 0, "HIGH" => 1, "MEDIUM" => 2, "LOW" => 3 }
findings.sort_by! { |finding| [order.fetch(finding[:severity], 9), finding[:package], finding[:id]] }

columns = [
  [:severity, "SEVERITY"],
  [:package, "PACKAGE"],
  [:installed, "INSTALLED"],
  [:fixed, "FIXED"],
  [:id, "VULNERABILITY"],
  [:target, "TARGET"]
]
widths = columns.to_h do |key, header|
  [key, ([header.length] + findings.map { |finding| finding[key].length }).max]
end

puts "Trivy findings in #{path}: #{findings.length}"
puts columns.map { |key, header| header.ljust(widths[key]) }.join("  ").rstrip
puts columns.map { |key, _| "-" * widths[key] }.join("  ")
findings.each do |finding|
  puts columns.map { |key, _| finding[key].ljust(widths[key]) }.join("  ").rstrip
end
