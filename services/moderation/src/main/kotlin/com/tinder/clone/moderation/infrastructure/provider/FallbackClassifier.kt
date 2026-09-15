package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent

/**
 * Keyless local/CI classifier. It reports every category as unsupported so the
 * policy returns HOLD. It must never turn words or regex hits into a content
 * decision, and it must never fabricate zero-risk evidence.
 */
class FallbackClassifier : ModerationClassifierPort {
    override fun classify(content: ModerationContent): ClassificationResult {
        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }
        return ClassificationResult(
            provider = "fallback",
            model = "unsupported",
            modelSnapshot = null,
            categories = categories,
            flagged = false,
            latencyMs = 0
        )
    }
}
