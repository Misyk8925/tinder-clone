#!/bin/sh
set -eu

cluster_nodes="${REDIS_CLUSTER_NODES:?REDIS_CLUSTER_NODES is required}"
cluster_replicas="${REDIS_CLUSTER_REPLICAS:-0}"
first_node="${cluster_nodes%% *}"

node_host() {
  echo "${1%:*}"
}

node_port() {
  echo "${1#*:}"
}

wait_for_ping() {
  host="$(node_host "$1")"
  port="$(node_port "$1")"
  until redis-cli -h "$host" -p "$port" ping >/dev/null 2>&1; do
    sleep 1
  done
}

expected_node_count() {
  count=0
  for _ in $cluster_nodes; do
    count=$((count + 1))
  done
  echo "$count"
}

# cluster_state:ok is not enough: a seed node can keep stale nodes.conf after
# Docker IP churn and advertise disconnected replicas as :0@0 / noaddr. Vert.x
# then dials port 0 and Deck Read stays 503.
topology_healthy() {
  host="$(node_host "$1")"
  port="$(node_port "$1")"
  info="$(redis-cli -h "$host" -p "$port" cluster info 2>/dev/null || true)"
  echo "$info" | grep -q 'cluster_state:ok' || return 1
  nodes="$(redis-cli -h "$host" -p "$port" cluster nodes 2>/dev/null || true)"
  echo "$nodes" | grep -Eq '(:0@0|[[:space:]]noaddr)' && return 1
  actual="$(printf '%s\n' "$nodes" | grep -c . || true)"
  expected="$(expected_node_count)"
  [ "$actual" -eq "$expected" ]
}

require_healthy_everywhere() {
  for node in $cluster_nodes; do
    if ! topology_healthy "$node"; then
      echo "Deck Read Redis Cluster topology is broken on ${node} (:0/noaddr, missing nodes, or not cluster_state:ok)." >&2
      echo "Wipe the deck-read-redis-*-data volumes and recreate the cluster." >&2
      redis-cli -h "$(node_host "$node")" -p "$(node_port "$node")" cluster nodes >&2 || true
      exit 1
    fi
  done
}

for node in $cluster_nodes; do
  wait_for_ping "$node"
done

wait_until_healthy_everywhere() {
  i=0
  while [ "$i" -lt 60 ]; do
    all_ok=1
    for node in $cluster_nodes; do
      topology_healthy "$node" || all_ok=0
    done
    if [ "$all_ok" -eq 1 ]; then
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  return 1
}

if topology_healthy "$first_node"; then
  if wait_until_healthy_everywhere; then
    exit 0
  fi
  require_healthy_everywhere
fi

if ! redis-cli --cluster create ${cluster_nodes} \
  --cluster-replicas "${cluster_replicas}" \
  --cluster-yes; then
  echo "Could not create the Deck Read Redis Cluster. If nodes already belong to a broken cluster, wipe the deck-read-redis-*-data volumes." >&2
  exit 1
fi

if wait_until_healthy_everywhere; then
  exit 0
fi

echo "Deck Read Redis Cluster did not become healthy after create." >&2
redis-cli -h "$(node_host "$first_node")" -p "$(node_port "$first_node")" cluster nodes >&2 || true
exit 1
