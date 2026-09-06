package com.tinder.clone.moderation.domain.model

data class ContextMessage(
    val contentId: String,
    val authorId: String,
    val text: String
) {
    init {
        require(contentId.isNotBlank()) { "Context content id must not be blank" }
        require(authorId.isNotBlank()) { "Context author id must not be blank" }
        require(text.isNotBlank()) { "Context text must not be blank" }
    }
}
