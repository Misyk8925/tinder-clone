package com.tinder.clone.moderation.application.commands.input

import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.domain.signals.AppliedPolicy

data class LlmAnalysisRequest(
    val content: ModerationContent,
    val applicationSignals: List<ApplicationSignal>,
    val classifierResult: ClassificationResult,
    val appliedPolicy: AppliedPolicy
) {
    init {
        require(content.text != null) { "LLM adjudication requires text content" }
        require(content.conversationContext.size <= MAX_CONTEXT_MESSAGES) {
            "LLM context must contain at most $MAX_CONTEXT_MESSAGES messages"
        }
        require(content.conversationContext.sumOf { it.text.toByteArray(Charsets.UTF_8).size } <= MAX_CONTEXT_BYTES) {
            "LLM context text must not exceed $MAX_CONTEXT_BYTES UTF-8 bytes"
        }
    }

    companion object {
        const val MAX_CONTEXT_MESSAGES = 20
        const val MAX_CONTEXT_BYTES = 16 * 1024
    }
}
