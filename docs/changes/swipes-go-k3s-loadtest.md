# Isolated k3s Swipes Go load test

## Compact scope record

- Intended outcome: a disposable k3s namespace running exactly three
  `swipes-go` replicas, a dedicated single-broker Kafka, and an in-cluster k6
  runner. The service must acknowledge a benchmark swipe only after the local
  Kafka broker acknowledges it.
- Out of scope: production Kubernetes, public traffic, JWT/profile-validation
  coverage, persistent benchmark data, Compose changes, and claims about
  production capacity.
- Affected contract: none. `SWIPES_INTERNAL_ONLY_BENCHMARK` is an internal
  process-startup guard, valid only in benchmark mode with the trusted secret,
  profile bypass, and profile-cache consumers disabled.
- Observable evidence:
  - Given the manifests, when they are statically checked, then there are three
    Go replicas, only ClusterIP services, no ingress/PVC/secret value, a
    separate Kafka, and a 12-partition `swipe-created` topic initializer.
  - Given internal-only benchmark mode, when JWT/JWKS and profile dependencies
    are absent, then startup configuration remains valid only with all benchmark
    safeguards enabled.
  - Given a ready deployment, when `run-k6.sh` creates a job, then k6 targets
    the internal `swipes-go:8040` service and reads its header secret only from
    the Kubernetes secret.
- Selected checks:
  - `ruby scripts/validate-swipes-go-k3s-loadtest.rb`
  - `bash -n infra/k3s/swipes-go-loadtest/{deploy,run-k6,destroy}.sh`
  - `GOCACHE=/tmp/tinder-swipes-go-cache go test ./...` and `go vet ./...` from
    `services/swipes-go`
  - A live `kubectl` rollout and k6 run are required before treating any
    capacity result as evidence.
- Rollback: run `infra/k3s/swipes-go-loadtest/destroy.sh --yes`; revert the
  benchmark-only Go guard if the disposable topology is removed. Neither action
  changes the normal Compose topology.
- Promotion check: this is one reversible, isolated load-test slice. It adds no
  production boundary or persisted data and requires no unresolved owner choice.

## Evidence

- Manifest structural validator passed.
- Shell syntax validator passed.
- Swipes Go tests and `go vet` passed.
- The embedded k6 script passed `k6 inspect --execution-requirements`.
- Local k3s smoke initially exposed a reserved `K6_DURATION` environment name
  that replaced the arrival-rate scenario; custom controls now use `LOAD_*`.
- Local runtime test passed on disposable k3s v1.31.4: three ready replicas,
  isolated Kafka, and a 200 requests/s / 10s in-cluster k6 run returned
  2,001/2,001 `202`s with 0 failures and 4.23 ms p95. The final Kafka offsets
  summed to 6,459, including the earlier 4,458-request configuration smoke.
- The temporary namespace and k3s container were removed after the test.
- The benchmark manifests retain scheduler requests but remove all CPU/memory
  cgroup limits for Kafka, ZooKeeper, Swipes Go, and k6. A follow-up local run
  must compare per-pod metric deltas under sustained concurrent traffic.

## Unlimited local balancing run

- Environment: a fresh disposable k3s v1.31.4-in-Docker cluster on the local
  eight-vCPU Docker runtime. Pod descriptions showed requests only; no container
  had a CPU or memory limit.
- Load: constant arrival rate of 8,000 requests/s for 15 seconds, with
  1,000 preallocated and up to 4,000 VUs. k6 completed 119,936 requests
  (7,993.56 requests/s), 0 HTTP failures, p95 6.49 ms, and 65 dropped
  iterations.
- Replica deltas: `41,101` (34.3%), `41,382` (34.5%), and `37,453` (31.2%).
  The maximum-minus-minimum spread was 3,929 requests (3.3 percentage points
  of the run), so the Kubernetes `ClusterIP` distributed this concurrent load
  across all three replicas.
- Kafka: all 12 partitions had ISR=1; offsets totaled exactly `119,936` and
  ranged from `9,824` to `10,227` records per partition. The accepted counter
  total also equaled `119,936`; all replicas reported zero queue-full events.
- Cleanup: the namespace and local k3s container were deleted after capture.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `compressed-small-change`: one disposable, reversible load-test slice |
| R.2 Project profile | Done | Repo-local `docs/changes` record; Go tests, Ruby manifest validator, Bash |
| R.3 Omitted phases identified | Done | Full-feature ceremony listed as Mode-omit below |
| P1.1–P1.12, P2.1–P2.8, P3.1–P3.7, P4.10 | Mode-omit | Compressed mode; no public/API/event/data contract changes |
| C.1 Outcome / out of scope | Done | Recorded above |
| C.2 Affected contract | Done | Internal startup guard only; no external contract |
| C.3 Acceptance check | Done | Structural manifest and configuration tests described above |
| C.4 Test levels and commands | Done | Ruby, Bash, targeted Go test/vet commands |
| C.5 Rollback | Done | Exact namespace-only destroy command and code revert note |
| C.6 Promotion check | Done | One isolated, reversible topology; no unresolved decision |
| P4.1 Slice plan | Done | Go guard, dedicated broker, three replicas, topic initializer, in-cluster k6 |
| P4.2 Primary evidence | Done | Configuration tests plus manifest validator |
| P4.3 Implementation | Done | `services/swipes-go` and `infra/k3s/swipes-go-loadtest` |
| P4.4 Test levels | Done | Unit/config, static manifest, shell syntax, k6 parse, smoke, and uncapped local k3s run passed |
| P4.5 Error path | Done | Missing benchmark safeguards fail config validation; deploy refuses a missing secret/image |
| P4.6 Review | Done | Explicit self-review after the live runner correction; specification and engineering fit checked |
| P4.7 Targeted defect review | Done | Fixed reserved `K6_DURATION` defect; uncapped run found no new source defect |
| P4.8 Quality gates | Done | `git diff --check`, Go tests/vet, manifest validator, Bash syntax |
| P4.9 Session handoff | N/A | Same context continued |
| P5.1 Build / validation | Done | Targeted Go and static validation checks |
| P5.2 Security scan | N/A | No production/public surface; manifests prohibit committed secret values |
| P5.3 Deploy | Done | Disposable local k3s only; namespace and container removed afterwards |
| P5.4 Smoke | Done | 200 requests/s smoke and 8,000 requests/s uncapped run passed |
| P5.5 Rollback | Done | Namespace-only destroy script |
| P5.6 Monitoring | Done | Captured k6, per-replica metrics, and Kafka offsets for the 15-second run |
| P5.7 Docs | Done | k3s README and this compact record |
| P5.8 Open-risk / blockers | Done | No blocker in the source change; live runtime evidence remains explicitly blocked |
