package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.domain.model.ContentType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ModerationRequestDto(
    @field:NotBlank @field:Size(max = 128) val contentId: String,
    val contentType: ContentType,
    val text: String? = null,
    @field:Size(max = 10) val imageUrls: List<@NotBlank @Size(max = 2048) String> = emptyList(),
    @field:Size(max = 35) val locale: String? = null,
    @field:Size(min = 2, max = 2) val country: String? = null,
    @field:Size(max = 128) val authorId: String? = null,
    @field:Size(max = 20) @field:Valid val conversationContext: List<ContextMessageDto> = emptyList()
)

data class ContextMessageDto(
    @field:NotBlank @field:Size(max = 128) val contentId: String,
    @field:NotBlank @field:Size(max = 128) val authorId: String,
    @field:NotBlank @field:Size(max = 4096) val text: String
)

data class ModerationResponseDto(
    val decisionId: UUID,
    val contentId: String,
    val decision: String,
    val reason: String?,
    val confidence: Double?,
    val evidence: EvidenceDto,
    val reviewTaskId: UUID? = null,
    val replayed: Boolean = false,
    val createdAt: Instant
)

data class EvidenceDto(
    val provider: String,
    val model: String,
    val modelSnapshot: String?,
    val categoryScores: List<CategoryScoreDto>,
    val applicationSignals: List<Map<String, Any>>,
    val adjudication: Map<String, Any?>?,
    val ruleHits: List<Map<String, Any?>>,
    val policyVersion: String,
    val policyScope: Map<String, String?>
)

data class CategoryScoreDto(
    val category: String,
    val score: Double?,
    val flagged: Boolean,
    val supported: Boolean
)

data class ApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val correlationId: String? = null,
    val fieldErrors: List<String> = emptyList()
)
