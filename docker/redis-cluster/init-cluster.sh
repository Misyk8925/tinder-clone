#!/bin/sh
set -eu

cluster_nodes="${REDIS_CLUSTER_NODES:?REDIS_CLUSTER_NODES is required}"
cluster_replicas="${REDIS_CLUSTER_REPLICAS:-0}"
first_node="${cluster_nodes%% *}"

until redis-cli -h "${first_node%:*}" -p "${first_node#*:}" ping >/dev/null 2>&1; do
  sleep 1
done

if redis-cli -h "${first_node%:*}" -p "${first_node#*:}" cluster info 2>/dev/null \
  | grep -q 'cluster_state:ok'; then
  exit 0
fi

redis-cli --cluster create ${cluster_nodes} \
  --cluster-replicas "${cluster_replicas}" \
  --cluster-yes
