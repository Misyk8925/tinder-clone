package com.tinder.clone.moderation.application.ports.input

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.output.ModerationResult

interface ModerateContentInputPort {
    fun handle(contentCmd: ContentCmd): ModerationResult
}
