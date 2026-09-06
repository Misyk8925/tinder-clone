package com.tinder.clone.moderation.application.commands.input

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ContextMessage

data class ContentCmd(
    val contentId: String,
    val contentType: ContentType,
    val text: String?,
    val imageUrls: List<String> = emptyList(),
    val locale: String? = null,
    val country: String? = null,
    val authorId: String? = null,
    val conversationContext: List<ContextMessage> = emptyList()
)
