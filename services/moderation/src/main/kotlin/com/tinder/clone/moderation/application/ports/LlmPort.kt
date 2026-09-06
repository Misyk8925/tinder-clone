package com.tinder.clone.moderation.application.ports

import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.LlmResult

interface LlmPort {
    fun analyzeContent(request: LlmAnalysisRequest): LlmResult
}
