package com.tinder.clone.moderation.application.ports

import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.domain.model.ModerationContent

interface ModerationClassifierPort {
    fun classify(content: ModerationContent): ClassificationResult
}
