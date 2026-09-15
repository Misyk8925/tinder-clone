package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.application.service.ModerationExecutionOutcome
import com.tinder.clone.moderation.application.service.ModerationExecutionService
import com.tinder.clone.moderation.infrastructure.http.ContextMessageDto
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class ModerationCommandConsumer(
    private val executions: ModerationExecutionService,
    private val objectMapper: ObjectMapper,
    private val validator: Validator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${moderation.kafka.commands-topic:moderation.commands.v1}"],
        groupId = "\${moderation.kafka.group-id:moderation-service}",
        autoStartup = "\${moderation.kafka.enabled:false}"
    )
    fun onCommand(payload: String) {
        process(payload)
    }

    fun process(payload: String) {
        val event = objectMapper.readValue(payload, ModerationRequestedEvent::class.java)
        require(event.schemaVersion == 1) {
            "Unsupported moderation command schema version"
        }
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
        require(event.correlationId.length in 1..128) {
            "Invalid moderation command correlation id"
        }
        require(
            validator.validate(request).isEmpty() &&
                event.text.orEmpty().length <= 65_536 &&
                event.country?.matches(Regex("^[A-Z]{2}$")) != false &&
                event.imageUrls.all { url -> runCatching { java.net.URI(url).isAbsolute }.getOrDefault(false) }
        ) {
            "Moderation command violates the v1 event contract"
        }
        when (val result = executions.execute(event.messageId.toString(), request)) {
            is ModerationExecutionOutcome.Evaluated ->
                log.info("Moderation command {} evaluated as {}", event.messageId, result.response.decision)
            is ModerationExecutionOutcome.Invalid, is ModerationExecutionOutcome.Throttled ->
                throw CommandNotEvaluatedException("Command ${event.messageId} was not evaluated")
        }
    }
}

class CommandNotEvaluatedException(message: String) : RuntimeException(message)
