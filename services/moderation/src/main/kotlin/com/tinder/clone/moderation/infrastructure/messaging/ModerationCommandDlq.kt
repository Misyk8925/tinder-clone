package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.config.ModerationKafkaProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

class ModerationCommandDlq(
    private val publisher: EventPublisher,
    private val objectMapper: ObjectMapper,
    private val kafka: ModerationKafkaProperties,
    private val clock: Clock = Clock.systemUTC()
) {
    fun publish(payload: String, error: Throwable, attempts: Int = MAX_ATTEMPTS) {
        val parsed = parseEnvelope(payload)
        val originalMessageId = parsed.messageId ?: UUID.nameUUIDFromBytes(payload.toByteArray())
        val rejected = mapOf(
            "schemaVersion" to 1,
            "messageId" to UUID.randomUUID().toString(),
            "correlationId" to (parsed.correlationId ?: originalMessageId.toString()),
            "occurredAt" to clock.instant().toString(),
            "originalMessageId" to originalMessageId.toString(),
            "failureCode" to "COMMAND_FAILED",
            "attempts" to attempts,
            "payloadSha256" to sha256(payload),
            "diagnostic" to "Processing failed: ${error::class.simpleName ?: "Exception"}"
        )
        publisher.send(
            kafka.commandsDlqTopic,
            originalMessageId.toString(),
            objectMapper.writeValueAsString(rejected)
        )
    }

    private fun parseEnvelope(payload: String): Envelope {
        val root = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: return Envelope()
        return Envelope(
            messageId = uuidOrNull(root, "messageId"),
            correlationId = root.path("correlationId").takeUnless { it.isMissingNode || it.isNull }?.stringValue()
        )
    }

    private fun uuidOrNull(root: JsonNode, field: String): UUID? =
        runCatching { UUID.fromString(root.path(field).stringValue()) }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private data class Envelope(val messageId: UUID? = null, val correlationId: String? = null)

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}