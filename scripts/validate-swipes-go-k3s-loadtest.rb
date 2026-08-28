#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

root = File.expand_path("..", __dir__)
manifest_dir = File.join(root, "infra", "k3s", "swipes-go-loadtest", "manifests")
documents = Dir[File.join(manifest_dir, "*.yaml")].flat_map do |path|
  YAML.load_stream(File.read(path)).compact
end

def find(documents, kind, name)
  documents.find { |document| document["kind"] == kind && document.dig("metadata", "name") == name }
end

def assert(condition, message)
  abort "load-test manifest validation failed: #{message}" unless condition
end

namespace = find(documents, "Namespace", "swipes-go-loadtest")
assert(namespace, "dedicated namespace is missing")

swipes = find(documents, "Deployment", "swipes-go")
assert(swipes.dig("spec", "replicas") == 3, "swipes-go must have exactly three replicas")
container = swipes.dig("spec", "template", "spec", "containers")&.find { |item| item["name"] == "swipes-go" }
assert(container && container["image"] == "__SWIPES_GO_IMAGE__", "swipes-go must require an explicit deploy image")
assert(container.dig("securityContext", "allowPrivilegeEscalation") == false, "swipes-go privilege escalation must be disabled")

config = find(documents, "ConfigMap", "swipes-go-loadtest-config")
assert(config.dig("data", "APP_ENV") == "benchmark", "swipes-go must run in benchmark mode")
assert(config.dig("data", "SWIPES_INTERNAL_ONLY_BENCHMARK") == "true", "isolated internal-only guard is missing")
assert(config.dig("data", "SWIPES_INTERNAL_BYPASS_PROFILE_CHECK") == "true", "profile bypass is required for the benchmark")
assert(config.dig("data", "PROFILE_CACHE_CONSUMERS_ENABLED") == "false", "profile consumers must be disabled")

kafka = find(documents, "Deployment", "swipes-kafka")
assert(kafka, "isolated Kafka deployment is missing")
kafka_env = kafka.dig("spec", "template", "spec", "containers", 0, "env").to_h { |item| [item["name"], item["value"]] }
assert(kafka_env["KAFKA_AUTO_CREATE_TOPICS_ENABLE"] == "false", "Kafka must disable auto-topic creation")

init = swipes.dig("spec", "template", "spec", "initContainers", 0)
assert(init && init.fetch("command").join(" ").include?("--partitions 12"), "swipe-created must be initialized with 12 partitions")

k6 = find(documents, "CronJob", "swipes-k6")
assert(k6 && k6.dig("spec", "suspend") == true, "k6 must be manually triggered from a suspended CronJob")
k6_env = k6.dig("spec", "jobTemplate", "spec", "template", "spec", "containers", 0, "env")
assert(k6_env.any? { |item| item["name"] == "INTERNAL_SWIPES_AUTH_SECRET" && item.dig("valueFrom", "secretKeyRef", "name") == "swipes-loadtest-auth" }, "k6 must read the benchmark secret from Kubernetes")
k6_script = find(documents, "ConfigMap", "swipes-k6-script").dig("data", "swipe-load-test.js")
assert(k6_script.include?("__ENV.LOAD_DURATION"), "k6 load controls must use the LOAD_ prefix")
assert(!k6_script.include?("__ENV.K6_"), "custom K6_ controls override k6 scenarios")

workloads = [
  find(documents, "Deployment", "swipes-kafka"),
  find(documents, "Deployment", "swipes-zookeeper"),
  find(documents, "Deployment", "swipes-go"),
  find(documents, "CronJob", "swipes-k6")
]
workload_containers = workloads.flat_map do |workload|
  template = workload.dig("spec", "template") || workload.dig("spec", "jobTemplate", "spec", "template")
  template.dig("spec", "containers")
end
assert(workload_containers.all? { |item| item.dig("resources", "requests") && !item.dig("resources", "limits") }, "load-test containers must retain requests but have no cgroup limits")

assert(documents.none? { |document| %w[Ingress PersistentVolumeClaim].include?(document["kind"]) }, "load-test topology must not expose ingress or persist state")
services = documents.select { |document| document["kind"] == "Service" }
assert(services.all? { |service| service.dig("spec", "type") == "ClusterIP" }, "all services must remain cluster-internal")
assert(documents.none? { |document| document.key?("stringData") }, "manifests must not contain secret values")

puts "Swipes Go k3s load-test manifests: OK"
