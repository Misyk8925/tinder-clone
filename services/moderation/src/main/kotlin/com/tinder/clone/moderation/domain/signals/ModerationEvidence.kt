package com.tinder.clone.moderation.domain.signals

import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.common.enums.LlmLabel
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.model.ContentType

enum class ApplicationSignalType { URL_COUNT, FLOOD, DUPLICATE, RATE_LIMIT, VERIFIED_SCAM_INDICATOR }

data class ApplicationSignal(
    val type: ApplicationSignalType,
    val attributes: Map<String, String> = emptyMap()
)

data class AdjudicationResult(
    val provider: String,
    val model: String?,
    val label: LlmLabel,
    val confidence: Double
) {
    init {
        require(provider.isNotBlank())
        require(model == null || model.isNotBlank())
        require(confidence in 0.0..1.0)
    }
}

data class RuleHit(
    val ruleId: String,
    val reason: Reason,
    val confidence: Double?,
    val category: ModerationCategory? = null
) {
    init {
        require(ruleId.isNotBlank())
        require(confidence == null || confidence in 0.0..1.0)
    }
}

data class AppliedPolicy(
    val version: String,
    val contentType: ContentType?,
    val locale: String?
)

data class ModerationEvidence(
    val classifierResult: ClassificationResult,
    val applicationSignals: List<ApplicationSignal> = emptyList(),
    val adjudication: AdjudicationResult? = null,
    val ruleHits: List<RuleHit> = emptyList(),
    val appliedPolicy: AppliedPolicy? = null
) {
    fun category(category: ModerationCategory) = classifierResult.categories.getValue(category)
    fun hasApplicationSignal(type: ApplicationSignalType): Boolean = applicationSignals.any { it.type == type }
    fun withAdjudication(result: AdjudicationResult): ModerationEvidence = copy(adjudication = result)
    fun withApplicationSignals(signals: List<ApplicationSignal>): ModerationEvidence =
        copy(applicationSignals = applicationSignals + signals)
    fun withRuleHit(hit: RuleHit): ModerationEvidence = copy(ruleHits = ruleHits + hit)
    fun withAppliedPolicy(policy: AppliedPolicy): ModerationEvidence = copy(appliedPolicy = policy)

}
