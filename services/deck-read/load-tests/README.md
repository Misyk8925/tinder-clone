# Materialized Deck Read load evidence

The benchmark targets a pre-materialized `GET /api/v2/deck?limit=20` viewer and emits one JSON result with RPS, error rate, and p50/p95/p99. It requires a valid JWT but never prints it.

Run the same image/configuration first through one API replica, then through a load-balanced two-replica endpoint:

```bash
cd services/deck-read/load-tests/go-deck-read-bench
AUTH_TOKEN="$TOKEN" URL=http://127.0.0.1:8040/api/v2/deck?limit=20 DURATION=30s go run .
AUTH_TOKEN="$TOKEN" URL=http://127.0.0.1:8040/api/v2/deck?limit=20 DURATION=30s \
  BASELINE_RPS=<one-replica-rps> BASELINE_P95_MS=<one-replica-p95-ms> go run .
```

The second run exits non-zero unless throughput is at least `1.7x`, p95 is no more than `1.2x`, and errors stay below `1%`. During both runs, verify `deck_read_requests{path="fast"}`, `deck_read_redis_page_latency`, Redis pool metrics, and zero Deck/Profiles downstream traffic.
