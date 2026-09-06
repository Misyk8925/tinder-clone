package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.LlmResult
import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.common.enums.LlmLabel

/** Safe no-op adjudication when Gemini is not configured. */
class FallbackLlmAdapter : LlmPort {
    override fun analyzeContent(request: LlmAnalysisRequest): LlmResult =
        LlmResult(LlmLabel.SAFE, 1.0, "fallback", "none")
}
