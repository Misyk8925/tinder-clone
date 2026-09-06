package com.tinder.clone.moderation.application.usecase

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.application.service.PreModerationProcessor
import com.tinder.clone.moderation.application.service.PreprocessingOutcome
import com.tinder.clone.moderation.application.service.EvidenceBuilder
import com.tinder.clone.moderation.domain.ModerationDomainService

class ModerateContentUsecase (
    private val classifierPort: ModerationClassifierPort,
    private val llmPort: LlmPort,
    private val domainService: ModerationDomainService,
    private val signalBuilder: EvidenceBuilder,
    private val preModerationProcessor: PreModerationProcessor
) : ModerateContentInputPort {

    override fun handle(contentCmd: ContentCmd): ModerationResult {

        val preprocessed = preModerationProcessor.process(contentCmd)
        if (preprocessed is PreprocessingOutcome.Invalid) return ModerationResult.Invalid(preprocessed.reason)
        if (preprocessed is PreprocessingOutcome.Throttled) return ModerationResult.Throttled(preprocessed.retryAfter)
        check(preprocessed is PreprocessingOutcome.Ready)
        val normalizedContent = preprocessed.content

        val classification = classifierPort.classify(normalizedContent)
        val initialEvidence = signalBuilder.fromClassification(classification, preprocessed.applicationSignals)
            .withAppliedPolicy(domainService.appliedPolicy(normalizedContent))

        val evidence = if (
            normalizedContent.text != null && domainService.requiresAdjudication(normalizedContent, initialEvidence)
        ) {
            val llmResult = llmPort.analyzeContent(
                LlmAnalysisRequest(
                    content = normalizedContent,
                    applicationSignals = initialEvidence.applicationSignals,
                    classifierResult = classification,
                    appliedPolicy = requireNotNull(initialEvidence.appliedPolicy)
                )
            )
            signalBuilder.enrichWithAdjudication(initialEvidence, llmResult)
        } else initialEvidence

        return this.domainService.moderate(normalizedContent, evidence)
    }
}
