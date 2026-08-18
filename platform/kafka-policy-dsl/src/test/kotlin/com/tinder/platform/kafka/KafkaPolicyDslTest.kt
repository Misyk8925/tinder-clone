package com.tinder.platform.kafka

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KafkaPolicyDslTest {

    @Test
    fun `critical producer cannot use leader-only acknowledgments`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            kafkaPolicies {
                topic("unsafe") {
                    owner = "test"
                    criticality = Criticality.CORRECTNESS
                    messageKey = "eventId"
                    producer("test", "unit") {
                        acknowledgments = Acknowledgments.LEADER
                        publishGuarantee = PublishGuarantee.BROKER_ACK
                    }
                }
            }
        }

        assertTrue(error.message!!.contains("acks=all"))
    }

    @Test
    fun `critical consumer cannot auto-commit`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            kafkaPolicies {
                topic("unsafe-consumer") {
                    owner = "test"
                    criticality = Criticality.CORRECTNESS
                    messageKey = "eventId"
                    producer("test", "unit") { publishGuarantee = PublishGuarantee.BROKER_ACK }
                    consumer("test", "test-group") {
                        commitPolicy = CommitPolicy.AUTO
                        idempotencyKey = "eventId"
                    }
                }
            }
        }

        assertTrue(error.message!!.contains("commit after processing"))
    }

    @Test
    fun `project catalog contains every active business topic and its DLT references`() {
        val catalog = TinderKafkaPolicies.catalog

        assertEquals(
            setOf(
                "swipe-created",
                "swipe-saved",
                "match.created",
                "profile.created",
                "profile.updated",
                "profile.deleted",
                "profile.deck-card-projection.v1",
                "deck.built.v1",
                "deck-read.materialization-requested.v1"
            ),
            catalog.topics.map { it.name }.toSet()
        )
        assertTrue(catalog.deadLetterTopics.contains("swipe-created.dlt"))
        assertTrue(catalog.deadLetterTopics.contains("match.created.dlt"))
        assertTrue(catalog.deadLetterTopics.contains("profile.deleted.dlt"))
    }

    @Test
    fun `catalog is exportable for a future read-only control plane API`() {
        val json = TinderKafkaPolicies.json()

        assertTrue(json.contains("\"controlMode\" : \"DESIRED_STATE_READ_ONLY\""))
        assertTrue(json.contains("\"name\" : \"swipe-created\""))
        assertTrue(json.contains("\"deadLetterTopics\""))
        assertTrue(json.contains("\"runtimeSources\""))
    }

    @Test
    fun `catalog exposes swipe throughput tuning and durable DLT policy`() {
        val swipe = TinderKafkaPolicies.catalog.topics.single { it.name == "swipe-created" }
        val javaProducer = swipe.producers.single { it.implementation == "java-reactor" }

        assertEquals(200_000, javaProducer.tuning.applicationQueueCapacity)
        assertEquals(500, javaProducer.tuning.batchRecords)
        assertEquals(131_072, javaProducer.tuning.kafkaBatchBytes)
        assertTrue(
            TinderKafkaPolicies.catalog.topics
                .flatMap { it.consumers }
                .all {
                    it.retry.deadLetter.acknowledgments == Acknowledgments.ALL &&
                        it.retry.deadLetter.idempotence
                }
        )
    }
}
