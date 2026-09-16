# swipes-demo (Java rollback)

This is **not** the demo or Compose production swipe path.

- Run and talk about [`services/swipes-go`](../swipes-go/README.md).
- This module exists as the Java original and the overlay in `docker-compose.swipes-java-rollback.yml`.
- Interview stories for matching reliability start at the outbox (`ProfileOutboxBatchProcessor`, `SwipeOutboxEventDispatcher`), not at this service.

Do not open this folder in a screening unless someone asks how the Go rewrite relates to the Spring version.
