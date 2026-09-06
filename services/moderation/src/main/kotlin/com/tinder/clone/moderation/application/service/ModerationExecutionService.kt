package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.domain.model.ContextMessage
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.infrastructure.http.CategoryScoreDto
import com.tinder.clone.moderation.infrastructure.http.EvidenceDto
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import io.micrometer.core.instrument.MeterRegistry

class IdempotencyConflictException : RuntimeException("Idempotency key payload differs")

sealed interface ModerationExecutionOutcome {
    data class Evaluated(val response: ModerationResponseDto) : ModerationExecutionOutcome
    data class Invalid(val reason: ValidationReason) : ModerationExecutionOutcome
    data class Throttled(val retryAfterSeconds: Long) : ModerationExecutionOutcome
}

class ModerationExecutionService(
    private val input: ModerateContentInputPort,
    private val objectMapper: ObjectMapper,
    private val store: ModerationDecisionStore = InMemoryModerationDecisionStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val meterRegistry: MeterRegistry? = null
) {
    fun execute(key: String, request: ModerationRequestDto): ModerationExecutionOutcome {
        val hash = sha256(objectMapper.writeValueAsBytes(request))
        store.findByIdempotencyKey(key)?.let { stored ->
            if (stored.requestHash != hash) throw IdempotencyConflictException()
            return ModerationExecutionOutcome.Evaluated(stored.response.copy(replayed = true))
        }
        return when (val result = input.handle(request.toCommand())) {
            is ModerationResult.Evaluated -> {
                val response = toResponse(request.contentId, result)
                val candidate = StoredModerationDecision(key, hash, request, response)
                when (val saved = store.saveOrGet(candidate)) {
                    is DecisionSaveResult.Created -> {
                        meterRegistry?.counter("moderation.decisions")?.increment()
                        ModerationExecutionOutcome.Evaluated(saved.decision.response)
                    }
                    is DecisionSaveResult.Existing -> {
                        if (saved.decision.requestHash != hash) throw IdempotencyConflictException()
                        ModerationExecutionOutcome.Evaluated(saved.decision.response.copy(replayed = true))
                    }
                }
            }
            is ModerationResult.Invalid -> ModerationExecutionOutcome.Invalid(result.reason)
            is ModerationResult.Throttled -> ModerationExecutionOutcome.Throttled(result.retryAfter.seconds.coerceAtLeast(1))
        }
    }

    fun get(id: UUID): ModerationResponseDto? = store.get(id)
    fun list(): List<ModerationResponseDto> = store.list()

    private fun ModerationRequestDto.toCommand() = ContentCmd(
        contentId, contentType, text, imageUrls, locale, country, authorId,
        conversationContext.map { ContextMessage(it.contentId, it.authorId, it.text) }
    )

    private fun toResponse(contentId: String, result: ModerationResult.Evaluated): ModerationResponseDto {
        val decision = result.decision
        return ModerationResponseDto(
            UUID.randomUUID(), contentId,
            when (decision) {
                Decision.Allow -> "ALLOW"; is Decision.Flag -> "FLAG"; is Decision.Block -> "BLOCK"; is Decision.Hold -> "HOLD"
            },
            when (decision) {
                Decision.Allow -> null; is Decision.Flag -> decision.reason.name; is Decision.Block -> decision.reason.name; is Decision.Hold -> decision.reason.name
            },
            when (decision) { is Decision.Flag -> decision.confidence; is Decision.Block -> decision.confidence; else -> null },
            toEvidence(result.evidence),
            reviewTaskId = if (decision is Decision.Flag || decision is Decision.Hold) UUID.randomUUID() else null,
            createdAt = Instant.now(clock)
        )
    }

    private fun toEvidence(evidence: ModerationEvidence): EvidenceDto {
        val classifier = evidence.classifierResult
        val policy = requireNotNull(evidence.appliedPolicy)
        return EvidenceDto(
            classifier.provider, classifier.model, classifier.modelSnapshot,
            classifier.categories.map { (category, value) -> CategoryScoreDto(category.name, value.score?.value, value.flagged, value.supported) },
            evidence.applicationSignals.map { mapOf("type" to it.type.name, "attributes" to it.attributes) },
            evidence.adjudication?.let { mapOf("provider" to it.provider, "model" to it.model, "label" to it.label.name, "confidence" to it.confidence) },
            evidence.ruleHits.map { mapOf("ruleId" to it.ruleId, "reason" to it.reason.name, "confidence" to it.confidence, "category" to it.category?.name) },
            policy.version, mapOf("contentType" to policy.contentType?.name, "locale" to policy.locale)
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
