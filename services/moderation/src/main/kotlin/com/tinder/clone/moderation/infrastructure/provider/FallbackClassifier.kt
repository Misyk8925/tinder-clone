package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent

/**
 * Used when no classifier key is configured so local and CI environments stay
 * usable. Clean text stays at zero so a published policy can ALLOW. Obvious
 * abuse phrases raise the matching OpenAI-mapped category above block
 * thresholds so profile and message writes can still be rejected without a
 * live provider.
 */
class FallbackClassifier : ModerationClassifierPort {
    override fun classify(content: ModerationContent): ClassificationResult {
        val haystack = normalize(
            listOfNotNull(content.text)
                .plus(content.conversationContext.map { it.text })
                .joinToString(" ")
        )
        val hits = RULES.filter { rule -> rule.phrases.any { haystack.contains(it) } }
            .map { it.category }
            .toSet()
        val categories = ModerationCategory.entries.associateWith { category ->
            when {
                category !in SUPPORTED -> CategoryScore.unsupported()
                category in hits -> CategoryScore.supported(0.95, true)
                else -> CategoryScore.supported(0.0, false)
            }
        }
        return ClassificationResult(
            provider = "fallback",
            model = "keyword",
            modelSnapshot = null,
            categories = categories,
            flagged = hits.isNotEmpty(),
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

        private val RULES = listOf(
            Rule(ModerationCategory.HATE, listOf("hate all", "should die", "nazi")),
            Rule(
                ModerationCategory.HARASSMENT,
                listOf("kill yourself", "kys", "worthless trash")
            ),
            Rule(
                ModerationCategory.HARASSMENT_THREATENING,
                listOf("i will kill you", "i'll kill you")
            ),
            Rule(ModerationCategory.VIOLENCE, listOf("i will kill you", "i'll kill you"))
        )

        private fun normalize(text: String): String =
            text.lowercase().replace(Regex("[^a-z0-9'\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private data class Rule(val category: ModerationCategory, val phrases: List<String>)
}
