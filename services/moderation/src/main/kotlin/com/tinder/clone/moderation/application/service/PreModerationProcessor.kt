package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.domain.signals.ApplicationSignalType
import java.time.Duration
import java.text.Normalizer

sealed interface PreprocessingOutcome {
    data class Ready(
        val content: ModerationContent,
        val applicationSignals: List<ApplicationSignal> = emptyList()
    ) : PreprocessingOutcome
    data class Invalid(val reason: ValidationReason) : PreprocessingOutcome
    data class Throttled(val retryAfter: Duration) : PreprocessingOutcome
}

enum class ValidationReason { EMPTY_CONTENT, PAYLOAD_TOO_LARGE, MALFORMED_PAYLOAD }

fun interface ApplicationSignalProvider {
    fun collect(content: ModerationContent): List<ApplicationSignal>
}

fun interface TrafficLimiter {
    fun retryAfter(content: ModerationContent): Duration?
}

/** Technical/content-neutral preprocessing. It never blocks based on meaning. */
open class PreModerationProcessor(
    private val maxTextChars: Int = 100_000,
    private val maxContextMessages: Int = 20,
    private val maxContextBytes: Int = 16 * 1024,
    private val signalProviders: List<ApplicationSignalProvider> = emptyList(),
    private val trafficLimiter: TrafficLimiter = TrafficLimiter { null }
) {
    fun process(command: ContentCmd): PreprocessingOutcome {
        val text = command.text?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        val normalizedContext = command.conversationContext.map { message ->
            message.copy(text = Normalizer.normalize(message.text, Normalizer.Form.NFKC))
        }
        if (command.contentId.isBlank() || command.imageUrls.any { it.isBlank() } ||
            command.locale?.isBlank() == true || command.country?.isBlank() == true ||
            command.authorId?.isBlank() == true
        ) return PreprocessingOutcome.Invalid(ValidationReason.MALFORMED_PAYLOAD)
        if (text.isNullOrBlank() && command.imageUrls.isEmpty()) {
            return PreprocessingOutcome.Invalid(ValidationReason.EMPTY_CONTENT)
        }
        if (text != null && text.length > maxTextChars) {
            return PreprocessingOutcome.Invalid(ValidationReason.PAYLOAD_TOO_LARGE)
        }
        if (normalizedContext.size > maxContextMessages ||
            normalizedContext.sumOf { it.text.toByteArray(Charsets.UTF_8).size } > maxContextBytes
        ) return PreprocessingOutcome.Invalid(ValidationReason.PAYLOAD_TOO_LARGE)
        return processReady(
            ModerationContent(
                id = command.contentId,
                type = command.contentType,
                text = text,
                imageUrls = command.imageUrls,
                locale = command.locale,
                country = command.country,
                authorId = command.authorId,
                conversationContext = normalizedContext
            )
        )
    }

    fun process(content: ModerationContent): PreprocessingOutcome {
        val text = content.text?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        val normalizedContext = content.conversationContext.map { message ->
            message.copy(text = Normalizer.normalize(message.text, Normalizer.Form.NFKC))
        }
        if (text.isNullOrBlank() && content.imageUrls.isEmpty()) return PreprocessingOutcome.Invalid(ValidationReason.EMPTY_CONTENT)
        if (text != null && text.length > maxTextChars) return PreprocessingOutcome.Invalid(ValidationReason.PAYLOAD_TOO_LARGE)
        if (normalizedContext.size > maxContextMessages ||
            normalizedContext.sumOf { it.text.toByteArray(Charsets.UTF_8).size } > maxContextBytes
        ) return PreprocessingOutcome.Invalid(ValidationReason.PAYLOAD_TOO_LARGE)
        val normalized = if (text == content.text && normalizedContext == content.conversationContext) {
            content
        } else content.copy(text = text, conversationContext = normalizedContext)
        return processReady(normalized)
    }

    private fun processReady(normalized: ModerationContent): PreprocessingOutcome {
        trafficLimiter.retryAfter(normalized)?.let { retryAfter ->
            require(!retryAfter.isNegative && !retryAfter.isZero) { "Retry-after duration must be positive" }
            return PreprocessingOutcome.Throttled(retryAfter)
        }
        val text = normalized.text
        val urlCount = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE).findAll(text.orEmpty()).count()
        val metadataSignals = if (urlCount == 0) emptyList() else listOf(
            ApplicationSignal(ApplicationSignalType.URL_COUNT, mapOf("count" to urlCount.toString()))
        )
        val applicationSignals = signalProviders.flatMap { it.collect(normalized) }
        return PreprocessingOutcome.Ready(normalized, metadataSignals + applicationSignals)
    }
}
