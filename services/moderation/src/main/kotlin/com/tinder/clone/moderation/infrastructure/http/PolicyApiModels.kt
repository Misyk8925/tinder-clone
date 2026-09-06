package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.policy.PolicyActivation
import com.tinder.clone.moderation.application.policy.PolicyDocument
import com.tinder.clone.moderation.application.policy.PolicyScopeDefinition
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.policy.CategoryThreshold
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class PolicyDraftRequestDto(
    @field:NotBlank @field:Size(max = 80) val version: String,
    @field:Size(max = 500) val description: String? = null,
    @field:Valid val scopes: List<PolicyScopeDto>
)

data class PolicyScopeDto(
    val contentType: ContentType? = null,
    @field:Size(max = 35) val locale: String? = null,
    @field:Valid val thresholds: List<CategoryThresholdDto> = emptyList()
)

data class CategoryThresholdDto(
    val category: ModerationCategory,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") val review: Double,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") val block: Double
)

data class PolicyVersionDto(
    val version: String,
    val description: String?,
    val scopes: List<PolicyScopeDto>,
    val status: String,
    val aggregateVersion: Long,
    val createdAt: Instant,
    val publishedAt: Instant?
)

data class PolicyValidationDto(val valid: Boolean, val errors: List<String>)

data class PolicyActivationDto(
    val activationId: UUID,
    val version: String,
    val previousVersion: String?,
    val scope: Map<String, String?>,
    val aggregateVersion: Long,
    val activatedAt: Instant
)

data class PolicyPreviewRequestDto(
    val policyVersion: String,
    val contentType: ContentType,
    val locale: String? = null,
    val evidence: Map<String, Any?>
)

fun PolicyDraftRequestDto.toDefinitions(): List<PolicyScopeDefinition> = scopes.map { scope ->
    val thresholds = scope.thresholds.associate { threshold ->
        threshold.category to CategoryThreshold(threshold.review, threshold.block)
    }
    require(thresholds.size == scope.thresholds.size) { "Threshold categories must be unique inside a scope" }
    PolicyScopeDefinition(scope.contentType, scope.locale, thresholds)
}

fun PolicyDocument.toDto() = PolicyVersionDto(
    version,
    description,
    scopes.map { scope ->
        PolicyScopeDto(
            scope.contentType,
            scope.locale,
            scope.thresholds.map { (category, value) -> CategoryThresholdDto(category, value.review, value.block) }
        )
    },
    status.name,
    aggregateVersion,
    createdAt,
    publishedAt
)

fun PolicyActivation.toDto() = PolicyActivationDto(
    activationId,
    version,
    previousVersion,
    mapOf("contentType" to contentType?.name, "locale" to locale),
    aggregateVersion,
    activatedAt
)
