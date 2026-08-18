package com.tinder.platform.kafka

@DslMarker
annotation class KafkaPolicyDsl

enum class Criticality {
    CORRECTNESS,
    REBUILDABLE
}

enum class Acknowledgments {
    ALL,
    LEADER
}

enum class PublishGuarantee {
    BROKER_ACK,
    TRANSACTIONAL_OUTBOX
}

enum class CommitPolicy {
    AFTER_PROCESSING,
    AUTO
}

data class EnvironmentCapacity(
    val partitions: Int,
    val replicationFactor: Int
)

data class ProducerPolicy(
    val service: String,
    val implementation: String,
    val acknowledgments: Acknowledgments,
    val idempotence: Boolean,
    val maxInFlight: Int,
    val retries: Int,
    val deliveryTimeoutMs: Long,
    val tuning: ProducerTuning,
    val publishGuarantee: PublishGuarantee
)

data class ProducerTuning(
    val kafkaBatchBytes: Int?,
    val batchRecords: Int?,
    val lingerMs: Long?,
    val clientBufferBytes: Long?,
    val applicationQueueCapacity: Int?,
    val workers: Int?
)

data class DeadLetterPolicy(
    val topic: String,
    val retentionMs: Long,
    val acknowledgments: Acknowledgments,
    val idempotence: Boolean
)

data class RetryPolicy(
    val maxRetries: Int,
    val backoffMs: Long,
    val deadLetter: DeadLetterPolicy
)

data class ConsumerPolicy(
    val service: String,
    val groupId: String,
    val commitPolicy: CommitPolicy,
    val retry: RetryPolicy,
    val idempotencyKey: String
)

data class TopicPolicy(
    val name: String,
    val owner: String,
    val criticality: Criticality,
    val messageKey: String,
    val retentionMs: Long,
    val cleanupPolicy: String,
    val local: EnvironmentCapacity,
    val production: EnvironmentCapacity,
    val producers: List<ProducerPolicy>,
    val consumers: List<ConsumerPolicy>,
    val runtimeSources: List<String>
)

data class KafkaPolicyCatalog(
    val schemaVersion: Int,
    val controlMode: String,
    val topics: List<TopicPolicy>
) {
    val deadLetterTopics: List<String> = topics
        .flatMap { topic -> topic.consumers.map { it.retry.deadLetter.topic } }
        .distinct()
        .sorted()
}

@KafkaPolicyDsl
class KafkaPoliciesBuilder {
    private val topics = mutableListOf<TopicPolicy>()

    fun topic(name: String, block: TopicPolicyBuilder.() -> Unit) {
        topics += TopicPolicyBuilder(name).apply(block).build()
    }

    internal fun build(): KafkaPolicyCatalog {
        val catalog = KafkaPolicyCatalog(
            schemaVersion = 1,
            controlMode = "DESIRED_STATE_READ_ONLY",
            topics = topics.toList()
        )
        KafkaPolicyValidator.validate(catalog)
        return catalog
    }
}

@KafkaPolicyDsl
class TopicPolicyBuilder internal constructor(private val name: String) {
    lateinit var owner: String
    lateinit var criticality: Criticality
    lateinit var messageKey: String
    var retentionMs: Long = 7 * 24 * 60 * 60 * 1_000L
    var cleanupPolicy: String = "delete"
    var local: EnvironmentCapacity = EnvironmentCapacity(partitions = 10, replicationFactor = 1)
    var production: EnvironmentCapacity = EnvironmentCapacity(partitions = 10, replicationFactor = 3)
    private val producers = mutableListOf<ProducerPolicy>()
    private val consumers = mutableListOf<ConsumerPolicy>()
    private val runtimeSources = mutableListOf<String>()

    fun producer(service: String, implementation: String, block: ProducerPolicyBuilder.() -> Unit) {
        producers += ProducerPolicyBuilder(service, implementation).apply(block).build()
    }

    fun consumer(service: String, groupId: String, block: ConsumerPolicyBuilder.() -> Unit) {
        consumers += ConsumerPolicyBuilder(service, groupId, name).apply(block).build()
    }

    fun runtimeSource(path: String) {
        runtimeSources += path
    }

    internal fun build() = TopicPolicy(
        name = name,
        owner = owner,
        criticality = criticality,
        messageKey = messageKey,
        retentionMs = retentionMs,
        cleanupPolicy = cleanupPolicy,
        local = local,
        production = production,
        producers = producers.toList(),
        consumers = consumers.toList(),
        runtimeSources = runtimeSources.toList()
    )
}

@KafkaPolicyDsl
class ProducerPolicyBuilder internal constructor(
    private val service: String,
    private val implementation: String
) {
    var acknowledgments: Acknowledgments = Acknowledgments.ALL
    var idempotence: Boolean = true
    var maxInFlight: Int = 5
    var retries: Int = 5
    var deliveryTimeoutMs: Long = 30_000
    var kafkaBatchBytes: Int? = null
    var batchRecords: Int? = null
    var lingerMs: Long? = null
    var clientBufferBytes: Long? = null
    var applicationQueueCapacity: Int? = null
    var workers: Int? = null
    lateinit var publishGuarantee: PublishGuarantee

    internal fun build() = ProducerPolicy(
        service = service,
        implementation = implementation,
        acknowledgments = acknowledgments,
        idempotence = idempotence,
        maxInFlight = maxInFlight,
        retries = retries,
        deliveryTimeoutMs = deliveryTimeoutMs,
        tuning = ProducerTuning(
            kafkaBatchBytes = kafkaBatchBytes,
            batchRecords = batchRecords,
            lingerMs = lingerMs,
            clientBufferBytes = clientBufferBytes,
            applicationQueueCapacity = applicationQueueCapacity,
            workers = workers
        ),
        publishGuarantee = publishGuarantee
    )
}

@KafkaPolicyDsl
class ConsumerPolicyBuilder internal constructor(
    private val service: String,
    private val groupId: String,
    private val topic: String
) {
    var commitPolicy: CommitPolicy = CommitPolicy.AFTER_PROCESSING
    var maxRetries: Int = 5
    var backoffMs: Long = 1_000
    var deadLetterTopic: String = "$topic.dlt"
    var deadLetterRetentionMs: Long = 14 * 24 * 60 * 60 * 1_000L
    var deadLetterAcknowledgments: Acknowledgments = Acknowledgments.ALL
    var deadLetterIdempotence: Boolean = true
    lateinit var idempotencyKey: String

    internal fun build() = ConsumerPolicy(
        service = service,
        groupId = groupId,
        commitPolicy = commitPolicy,
        retry = RetryPolicy(
            maxRetries,
            backoffMs,
            DeadLetterPolicy(
                topic = deadLetterTopic,
                retentionMs = deadLetterRetentionMs,
                acknowledgments = deadLetterAcknowledgments,
                idempotence = deadLetterIdempotence
            )
        ),
        idempotencyKey = idempotencyKey
    )
}

fun kafkaPolicies(block: KafkaPoliciesBuilder.() -> Unit): KafkaPolicyCatalog =
    KafkaPoliciesBuilder().apply(block).build()

object KafkaPolicyValidator {
    fun validate(catalog: KafkaPolicyCatalog) {
        require(catalog.topics.map { it.name }.distinct().size == catalog.topics.size) {
            "Kafka topic names must be unique"
        }
        catalog.topics.forEach(::validateTopic)
    }

    private fun validateTopic(topic: TopicPolicy) {
        require(topic.name.isNotBlank()) { "Kafka topic name must not be blank" }
        require(topic.owner.isNotBlank()) { "${topic.name}: owner must not be blank" }
        require(topic.messageKey.isNotBlank()) { "${topic.name}: message key must be declared" }
        require(topic.retentionMs > 0) { "${topic.name}: retention must be positive" }
        require(topic.local.partitions > 0 && topic.production.partitions > 0) {
            "${topic.name}: partition counts must be positive"
        }
        require(topic.local.replicationFactor > 0 && topic.production.replicationFactor > 0) {
            "${topic.name}: replication factors must be positive"
        }

        if (topic.criticality == Criticality.CORRECTNESS) {
            require(topic.producers.isNotEmpty()) { "${topic.name}: correctness topic needs a producer" }
            topic.producers.forEach { producer ->
                require(producer.acknowledgments == Acknowledgments.ALL) {
                    "${topic.name}/${producer.service}: correctness producer must use acks=all"
                }
                require(producer.idempotence) {
                    "${topic.name}/${producer.service}: correctness producer must enable idempotence"
                }
                require(producer.maxInFlight in 1..5) {
                    "${topic.name}/${producer.service}: maxInFlight must be between 1 and 5"
                }
                require(producer.deliveryTimeoutMs > 0) {
                    "${topic.name}/${producer.service}: delivery timeout must be positive"
                }
            }
            topic.consumers.forEach { consumer ->
                require(consumer.commitPolicy == CommitPolicy.AFTER_PROCESSING) {
                    "${topic.name}/${consumer.service}: correctness consumer must commit after processing"
                }
                require(consumer.retry.maxRetries >= 0 && consumer.retry.backoffMs >= 0) {
                    "${topic.name}/${consumer.service}: retry policy must not be negative"
                }
                require(consumer.retry.deadLetter.topic.isNotBlank()) {
                    "${topic.name}/${consumer.service}: DLT must be declared"
                }
                require(consumer.retry.deadLetter.retentionMs > 0) {
                    "${topic.name}/${consumer.service}: DLT retention must be positive"
                }
                require(consumer.retry.deadLetter.acknowledgments == Acknowledgments.ALL &&
                        consumer.retry.deadLetter.idempotence) {
                    "${topic.name}/${consumer.service}: DLT producer must be durable and idempotent"
                }
                require(consumer.idempotencyKey.isNotBlank()) {
                    "${topic.name}/${consumer.service}: idempotency key must be declared"
                }
            }
        }
    }
}
