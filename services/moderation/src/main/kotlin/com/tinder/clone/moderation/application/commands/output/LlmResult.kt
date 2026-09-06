package com.tinder.clone.moderation.application.commands.output

import com.tinder.clone.moderation.common.enums.LlmLabel

data class LlmResult(
    val label: LlmLabel,
    val confidence: Double,
    val provider: String = "llm",
    val model: String? = null
)
