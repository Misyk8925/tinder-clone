# swipes-demo (Java rollback)

This is **not** the demo or Compose production swipe path.

- Run and talk about [`services/swipes-go`](../swipes-go/README.md).
- This module exists as the Java original. No Compose file builds it; to roll back, point the `swipes` build context in `docker-compose.yml` at `./services/swipes-demo` and set `SPRING_PROFILES_ACTIVE: prod`.
- Interview stories for matching reliability start at the outbox (`ProfileOutboxBatchProcessor`, `SwipeOutboxEventDispatcher`), not at this service.

Do not open this folder in a screening unless someone asks how the Go rewrite relates to the Spring version.
