package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.application.service.ModerationExecutionOutcome
import com.tinder.clone.moderation.application.service.ModerationExecutionService
import com.tinder.clone.moderation.config.ModerationKafkaProperties
import com.tinder.clone.moderation.infrastructure.http.ContextMessageDto
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class ModerationCommandConsumer(
    private val executions: ModerationExecutionService,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafka: ModerationKafkaProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val attempts = mutableMapOf<UUID, Int>()

    @KafkaListener(
        topics = ["\${moderation.kafka.commands-topic:moderation.commands.v1}"],
        groupId = "\${moderation.kafka.group-id:moderation-service}",
        autoStartup = "\${moderation.kafka.enabled:false}"
    )
    fun onCommand(payload: String) {
        val event = objectMapper.readValue(payload, ModerationRequestedEvent::class.java)
        val request = ModerationRequestDto(
            contentId = event.contentId,
            contentType = event.contentType,
            text = event.text,
            imageUrls = event.imageUrls,
            locale = event.locale,
            country = event.country,
            authorId = event.authorId,
            conversationContext = event.conversationContext.map {
                ContextMessageDto(it.contentId, it.authorId, it.text)
            }
        )
        try {
            when (val result = executions.execute(event.messageId.toString(), request)) {
                is ModerationExecutionOutcome.Evaluated -> attempts.remove(event.messageId)
                is ModerationExecutionOutcome.Invalid, is ModerationExecutionOutcome.Throttled ->
                    throw IllegalStateException("Command ${event.messageId} was not evaluated")
            }
        } catch (error: Exception) {
            val count = (attempts[event.messageId] ?: 0) + 1
            attempts[event.messageId] = count
            log.warn("Moderation command {} failed ({}/5)", event.messageId, count, error)
            if (count >= 5) {
                publishDlq(event, payload, error)
                attempts.remove(event.messageId)
                return
            }
            throw error
        }
    }

    private fun publishDlq(event: ModerationRequestedEvent, payload: String, error: Exception) {
        val rejected = mapOf(
            "schemaVersion" to 1,
            "messageId" to UUID.randomUUID().toString(),
            "correlationId" to event.correlationId,
            "occurredAt" to Instant.now().toString(),
            "originalMessageId" to event.messageId.toString(),
            "failureCode" to "COMMAND_FAILED",
            "attempts" to 5,
            "payloadSha256" to sha256(payload),
            "diagnostic" to error.message?.take(500)
        )
        kafkaTemplate.send(kafka.commandsDlqTopic, event.messageId.toString(), objectMapper.writeValueAsString(rejected))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
