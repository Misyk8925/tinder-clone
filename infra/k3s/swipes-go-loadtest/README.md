# Disposable k3s: 3× Swipes Go + isolated Kafka + k6

This is a **benchmark-only**, namespaced k3s topology. It creates three
`swipes-go` replicas behind an internal `ClusterIP` service and an independent
single-broker Kafka/ZooKeeper pair. k6 runs in the same namespace, so neither
the test traffic nor `swipe-created` records reach the normal Compose Kafka or
any downstream production consumers.

The topology uses the service's guarded internal-only benchmark mode. It
accepts only the `X-Internal-Auth` secret, bypasses profile lookups, disables
profile event consumers, and is rejected unless `APP_ENV=benchmark`. It is not
a production deployment or a public authentication test.

## Prerequisites

- A disposable k3s cluster selected by the current `kubectl` context.
- A registry image of this checkout. Use an immutable digest, not `latest`.
- `kubectl`, `awk`, and a cluster with enough allocatable capacity for the
  requested Kafka, three app replicas, and k6 resources.

The manifests deliberately create no `Ingress`, `NodePort`, `LoadBalancer`,
PVC, database, Redis, or connection to the Compose namespace. All broker data
is ephemeral and disappears with namespace cleanup.

## Deploy and run

From this directory, first create a unique local secret. Do not commit it or
reuse a production value.

```bash
kubectl create namespace swipes-go-loadtest
kubectl -n swipes-go-loadtest create secret generic swipes-loadtest-auth \
  --from-literal=internal-auth-secret='<random benchmark-only value>'

SWIPES_GO_IMAGE='registry.example/swipes-go@sha256:<digest>' ./deploy.sh
./run-k6.sh
```

`run-k6.sh` defaults to a 2,000 requests/s, 30-second constant-arrival-rate
run. Override only the test parameters you need:

```bash
LOAD_RATE=8000 LOAD_DURATION=60s LOAD_PRE_ALLOCATED_VUS=1800 LOAD_MAX_VUS=9000 \
  LOAD_P95_MS=250 ./run-k6.sh
```

The `swipe-created` topic is explicitly created with 12 partitions and Kafka
auto-topic creation is disabled. The Go producer keeps `acks=all`; a `202`
therefore still requires broker acknowledgement. Record the k6 summary, app
metrics (`/actuator/metrics` via `kubectl port-forward` if needed), pod CPU and
memory, and Kafka lag before comparing runs.

## Cleanup and interpretation

Delete the whole disposable environment after a run:

```bash
./destroy.sh --yes
```

This removes only the exact `swipes-go-loadtest` namespace. The manifests keep
scheduler requests but intentionally set no CPU or memory cgroup limits, so a
run is bounded by the disposable cluster host instead. Results are still not
production capacity claims and do not prove the authenticated/profile-validation
path.
