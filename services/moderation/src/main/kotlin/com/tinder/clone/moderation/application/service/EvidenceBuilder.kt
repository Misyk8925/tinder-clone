package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.commands.output.LlmResult
import com.tinder.clone.moderation.domain.signals.AdjudicationResult
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.domain.signals.ModerationEvidence

open class EvidenceBuilder {
    fun fromClassification(
        result: ClassificationResult,
        applicationSignals: List<ApplicationSignal> = emptyList()
    ): ModerationEvidence = ModerationEvidence(
        classifierResult = result,
        applicationSignals = applicationSignals
    )

    fun enrichWithAdjudication(evidence: ModerationEvidence, result: LlmResult): ModerationEvidence =
        evidence.withAdjudication(
            AdjudicationResult(
                provider = result.provider,
                model = result.model,
                label = result.label,
                confidence = result.confidence
            )
        )
}
