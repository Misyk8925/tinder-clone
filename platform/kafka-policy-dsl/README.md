# Kafka policy DSL

This module is the typed desired-state catalog for Kafka delivery policies. It describes every
business topic, its owners, producer guarantees, consumer retry/DLT behavior, environment capacity,
and the runtime configuration files that currently implement the policy.

The DSL is intentionally not a second Kafka admin client. `controlMode=DESIRED_STATE_READ_ONLY`
means a future control-plane API can return the normalized catalog and compare it with broker/client
metrics without allowing a UI request to mutate production Kafka directly. A separate reviewed
reconciliation workflow can be added later.

```kotlin
topic("match.created") {
    owner = "consumer"
    criticality = Criticality.CORRECTNESS
    messageKey = "eventId"
    producer("consumer", "transactional-outbox") {
        publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
    }
    consumer("match", "consumer-service-groupmatch.created") {
        idempotencyKey = "profilePair"
    }
}
```

Run `mvn test` to validate policies. The `main` function prints JSON suitable for a future API or
catalog artifact.
