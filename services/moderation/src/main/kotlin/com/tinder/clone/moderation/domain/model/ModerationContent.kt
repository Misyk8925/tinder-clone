package com.tinder.clone.moderation.domain.model

data class ModerationContent(
    val id: String,
    val type: ContentType,
    val text: String?,
    val imageUrls: List<String> = emptyList(),
    val locale: String? = null,
    val country: String? = null,
    val authorId: String? = null,
    val conversationContext: List<ContextMessage> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Content id must not be blank" }
        require(text?.isNotBlank() == true || imageUrls.isNotEmpty()) {
            "Content must contain non-blank text or at least one image"
        }
        require(imageUrls.all { it.isNotBlank() }) { "Image URLs must not be blank" }
        require(locale == null || locale.isNotBlank()) { "Locale must not be blank" }
        require(country == null || country.isNotBlank()) { "Country must not be blank" }
        require(authorId == null || authorId.isNotBlank()) { "Author id must not be blank" }
    }
}
