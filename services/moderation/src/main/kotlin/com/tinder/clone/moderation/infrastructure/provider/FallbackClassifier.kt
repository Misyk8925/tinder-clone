package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent

/**
 * Used when no classifier key is configured so local and CI environments stay
 * usable. Every OpenAI-mapped category is reported as supported with a zero
 * score, which lets a published policy decide ALLOW instead of HOLD.
 */
class FallbackClassifier : ModerationClassifierPort {
    override fun classify(content: ModerationContent): ClassificationResult {
        val categories = ModerationCategory.entries.associateWith { category ->
            if (category in SUPPORTED) CategoryScore.supported(0.0, false) else CategoryScore.unsupported()
        }
        return ClassificationResult(
            provider = "fallback",
            model = "none",
            modelSnapshot = null,
            categories = categories,
            flagged = false,
            latencyMs = 0
        )
    }

    companion object {
        private val SUPPORTED = setOf(
            ModerationCategory.HARASSMENT,
            ModerationCategory.HARASSMENT_THREATENING,
            ModerationCategory.HATE,
            ModerationCategory.HATE_THREATENING,
            ModerationCategory.SEXUAL_CONTENT,
            ModerationCategory.SEXUAL_MINORS,
            ModerationCategory.SELF_HARM,
            ModerationCategory.VIOLENCE
        )
    }
}
