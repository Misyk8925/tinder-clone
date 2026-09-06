package com.tinder.clone.moderation.application.commands.output

import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.Score

data class ClassificationResult(
    val provider: String,
    val model: String,
    val modelSnapshot: String?,
    val categories: Map<ModerationCategory, CategoryScore>,
    val flagged: Boolean,
    val latencyMs: Long
) {
    init {
        require(provider.isNotBlank()) { "Classifier provider must not be blank" }
        require(model.isNotBlank()) { "Classifier model must not be blank" }
        require(modelSnapshot == null || modelSnapshot.isNotBlank()) {
            "Classifier model snapshot must not be blank"
        }
        require(categories.keys == ModerationCategory.entries.toSet()) {
            "Classifier result must explicitly include every moderation category"
        }
        require(flagged == categories.values.any { it.flagged }) {
            "Classifier aggregate flag must match category flags"
        }
        require(latencyMs >= 0) { "Classifier latency must not be negative" }
    }
}

data class CategoryScore(
    val score: Score?,
    val flagged: Boolean,
    val supported: Boolean
) {
    init {
        require(supported == (score != null)) {
            "Supported categories require a score; unsupported categories must not have one"
        }
        require(!flagged || supported) { "Unsupported categories cannot be flagged" }
    }

    companion object {
        fun supported(score: Double, flagged: Boolean): CategoryScore =
            CategoryScore(score = Score(score), flagged = flagged, supported = true)

        fun unsupported(): CategoryScore =
            CategoryScore(score = null, flagged = false, supported = false)
    }
}
