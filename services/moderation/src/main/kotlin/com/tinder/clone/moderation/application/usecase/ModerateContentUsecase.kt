package com.tinder.clone.moderation.application.usecase

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.application.service.PreModerationProcessor
import com.tinder.clone.moderation.application.service.PreprocessingOutcome
import com.tinder.clone.moderation.application.service.EvidenceBuilder
import com.tinder.clone.moderation.domain.ModerationDomainService
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.infrastructure.provider.ProviderException

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

        val classification = try {
            classifyWithRetry(normalizedContent)
        } catch (error: ProviderException) {
            return holdForProvider(normalizedContent, error, preprocessed.applicationSignals)
        }
        val initialEvidence = signalBuilder.fromClassification(classification, preprocessed.applicationSignals)
            .withAppliedPolicy(domainService.appliedPolicy(normalizedContent))

        val evidence = if (
            normalizedContent.text != null && domainService.requiresAdjudication(normalizedContent, initialEvidence)
        ) {
            try {
                val llmResult = analyzeWithRetry(
                    LlmAnalysisRequest(
                        content = normalizedContent,
                        applicationSignals = initialEvidence.applicationSignals,
                        classifierResult = classification,
                        appliedPolicy = requireNotNull(initialEvidence.appliedPolicy)
                    )
                )
                signalBuilder.enrichWithAdjudication(initialEvidence, llmResult)
            } catch (error: ProviderException) {
                return holdForProvider(normalizedContent, error, preprocessed.applicationSignals, classification)
            }
        } else initialEvidence

        return this.domainService.moderate(normalizedContent, evidence)
    }

    private fun classifyWithRetry(content: ModerationContent): ClassificationResult {
        var last: ProviderException? = null
        repeat(PROVIDER_ATTEMPTS) {
            try {
                return classifierPort.classify(content)
            } catch (error: ProviderException) {
                last = error
            }
        }
        throw requireNotNull(last)
    }

    private fun analyzeWithRetry(request: LlmAnalysisRequest) = run {
        var last: ProviderException? = null
        repeat(PROVIDER_ATTEMPTS) {
            try {
                return@run llmPort.analyzeContent(request)
            } catch (error: ProviderException) {
                last = error
            }
        }
        throw requireNotNull(last)
    }

    private fun holdForProvider(
        content: ModerationContent,
        error: ProviderException,
        signals: List<ApplicationSignal>,
        classification: ClassificationResult? = null
    ): ModerationResult.Evaluated {
        val fallback = classification ?: ClassificationResult(
            provider = error.provider,
            model = "unavailable",
            modelSnapshot = null,
            categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() },
            flagged = false,
            latencyMs = 0
        )
        val evidence = signalBuilder.fromClassification(fallback, signals)
            .withAppliedPolicy(domainService.appliedPolicy(content))
        return ModerationResult.Evaluated(Decision.Hold(Reason.PROVIDER_UNAVAILABLE), evidence)
    }

    companion object {
        const val PROVIDER_ATTEMPTS = 2
    }
}
