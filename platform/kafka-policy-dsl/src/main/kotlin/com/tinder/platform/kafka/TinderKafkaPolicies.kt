package com.tinder.platform.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

object TinderKafkaPolicies {
    val catalog: KafkaPolicyCatalog = kafkaPolicies {
        topic("swipe-created") {
            owner = "swipes"
            criticality = Criticality.CORRECTNESS
            messageKey = "profile1Id"
            producer("swipes-demo", "java-reactor") {
                publishGuarantee = PublishGuarantee.BROKER_ACK
                kafkaBatchBytes = 131_072
                batchRecords = 500
                lingerMs = 20
                clientBufferBytes = 67_108_864
                applicationQueueCapacity = 200_000
                workers = 4
            }
            producer("swipes-go", "go-candidate") {
                publishGuarantee = PublishGuarantee.BROKER_ACK
                batchRecords = 500
                lingerMs = 1
                applicationQueueCapacity = 200_000
                workers = 4
            }
            consumer("consumer", "consumer-service-group") { idempotencyKey = "eventId" }
            runtimeSource("services/swipes-demo/src/main/resources/application.yaml")
            runtimeSource("services/swipes-demo/src/main/java/com/example/swipes_demo/SwipeProducer.java")
            runtimeSource("services/swipes-go/internal/kafka/producer.go")
            runtimeSource("services/consumer/src/main/resources/application.yml")
        }

        topic("swipe-saved") {
            owner = "consumer"
            criticality = Criticality.CORRECTNESS
            messageKey = "profile1Id"
            producer("consumer", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
            }
            consumer("deck", "deck-service-group") { idempotencyKey = "eventId" }
            consumer("deck-read", "deck-read-swipe-projection-v1") { idempotencyKey = "eventId" }
            runtimeSource("services/consumer/src/main/resources/application.yml")
            runtimeSource("services/deck/src/main/resources/application.yml")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
        }

        topic("match.created") {
            owner = "consumer"
            criticality = Criticality.CORRECTNESS
            messageKey = "eventId"
            producer("consumer", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
            }
            consumer("match", "consumer-service-groupmatch.created") { idempotencyKey = "profilePair" }
            consumer("deck-read", "deck-read-match-projection-v1") { idempotencyKey = "eventId" }
            runtimeSource("services/consumer/src/main/resources/application.yml")
            runtimeSource("services/match/src/main/resources/application.yaml")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
        }

        topic("profile.created") {
            owner = "profiles"
            criticality = Criticality.CORRECTNESS
            messageKey = "profileId"
            producer("profiles", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
                retries = 3
            }
            consumer("consumer", "consumer-service-group-profile") { idempotencyKey = "profileId" }
            consumer("swipes-demo", "swipe-service") { idempotencyKey = "profileId" }
            consumer("swipes-go", "swipes-profile-cache-profile.created") { idempotencyKey = "profileId" }
            runtimeSource("services/profiles/src/main/resources/application.yml")
            runtimeSource("services/consumer/src/main/resources/application.yml")
            runtimeSource("services/swipes-demo/src/main/resources/application.yaml")
            runtimeSource("services/swipes-go/internal/config/config.go")
        }

        topic("profile.updated") {
            owner = "profiles"
            criticality = Criticality.CORRECTNESS
            messageKey = "profileId"
            producer("profiles", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
                retries = 3
            }
            consumer("deck", "deck-service-group") { idempotencyKey = "eventId" }
            runtimeSource("services/profiles/src/main/resources/application.yml")
            runtimeSource("services/deck/src/main/resources/application.yml")
        }

        topic("profile.deleted") {
            owner = "profiles"
            criticality = Criticality.CORRECTNESS
            messageKey = "profileId"
            producer("profiles", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
                retries = 3
            }
            consumer("consumer", "consumer-service-group-profile-delete") { idempotencyKey = "profileId" }
            consumer("deck", "deck-service-group") { idempotencyKey = "eventId" }
            consumer("swipes-demo", "swipe-service") { idempotencyKey = "profileId" }
            consumer("swipes-go", "swipes-profile-cache-profile.deleted") { idempotencyKey = "profileId" }
            runtimeSource("services/profiles/src/main/resources/application.yml")
            runtimeSource("services/consumer/src/main/resources/application.yml")
            runtimeSource("services/deck/src/main/resources/application.yml")
            runtimeSource("services/swipes-demo/src/main/resources/application.yaml")
            runtimeSource("services/swipes-go/internal/config/config.go")
        }

        topic("profile.deck-card-projection.v1") {
            owner = "profiles"
            criticality = Criticality.REBUILDABLE
            messageKey = "profileId"
            producer("profiles", "transactional-outbox") {
                publishGuarantee = PublishGuarantee.TRANSACTIONAL_OUTBOX
                retries = 3
            }
            consumer("deck-read", "deck-read-profile-projection-v1") { idempotencyKey = "profileId+version" }
            runtimeSource("services/profiles/src/main/resources/application.yml")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
        }

        topic("deck.built.v1") {
            owner = "deck"
            criticality = Criticality.REBUILDABLE
            messageKey = "viewerProfileId"
            local = EnvironmentCapacity(partitions = 48, replicationFactor = 1)
            production = EnvironmentCapacity(partitions = 48, replicationFactor = 3)
            producer("deck", "broker-ack") { publishGuarantee = PublishGuarantee.BROKER_ACK }
            consumer("deck-read", "deck-read-deck-built-v1") { idempotencyKey = "eventId" }
            runtimeSource("services/deck/src/main/resources/application.yml")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
            runtimeSource("docker-compose.yml")
        }

        topic("deck-read.materialization-requested.v1") {
            owner = "deck-read"
            criticality = Criticality.REBUILDABLE
            messageKey = "viewerProfileId"
            local = EnvironmentCapacity(partitions = 48, replicationFactor = 1)
            production = EnvironmentCapacity(partitions = 48, replicationFactor = 3)
            producer("deck-read", "broker-ack") { publishGuarantee = PublishGuarantee.BROKER_ACK }
            consumer("deck-read", "deck-read-materialization-v1") { idempotencyKey = "requestId" }
            runtimeSource("services/deck-read/src/main/resources/application.properties")
            runtimeSource("docker-compose.yml")
        }
    }

    fun json(): String = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValueAsString(catalog)
}

fun main(args: Array<String>) {
    val json = TinderKafkaPolicies.json()
    if (args.isNotEmpty()) {
        val file = java.io.File(args[0])
        file.parentFile?.mkdirs()
        file.writeText(json)
    } else {
        println(json)
    }
}
