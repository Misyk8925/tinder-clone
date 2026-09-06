package com.tinder.clone.moderation.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModerationContentTest {

    @Test
    fun `given message metadata and context when content is created then classifier input is complete`() {
        val context = listOf(
            ContextMessage(
                contentId = "message-previous",
                authorId = "user-2",
                text = "This is a quote from the film"
            )
        )

        val content = ModerationContent(
            id = "message-1",
            type = ContentType.MESSAGE,
            text = "I will kill you",
            imageUrls = listOf("https://cdn.example/image.jpg"),
            locale = "en-AT",
            country = "AT",
            authorId = "user-1",
            conversationContext = context
        )

        assertEquals("message-1", content.id)
        assertEquals(ContentType.MESSAGE, content.type)
        assertEquals("I will kill you", content.text)
        assertEquals(listOf("https://cdn.example/image.jpg"), content.imageUrls)
        assertEquals("en-AT", content.locale)
        assertEquals("AT", content.country)
        assertEquals("user-1", content.authorId)
        assertEquals(context, content.conversationContext)
    }

    @Test
    fun `given text without images when content is created then text input is accepted`() {
        val content = ModerationContent(
            id = "profile-1",
            type = ContentType.PROFILE_DESCRIPTION,
            text = "Profile text"
        )

        assertEquals("Profile text", content.text)
        assertEquals(emptyList(), content.imageUrls)
        assertNull(content.locale)
        assertNull(content.country)
        assertNull(content.authorId)
        assertEquals(emptyList(), content.conversationContext)
    }

    @Test
    fun `given neither text nor images when content is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            ModerationContent(
                id = "message-1",
                type = ContentType.MESSAGE,
                text = null
            )
        }
    }

    @Test
    fun `given image without text when content is created then multimodal input is accepted`() {
        val content = ModerationContent(
            id = "photo-1",
            type = ContentType.PHOTO,
            text = null,
            imageUrls = listOf("https://cdn.example/photo.jpg")
        )

        assertEquals(null, content.text)
        assertEquals(listOf("https://cdn.example/photo.jpg"), content.imageUrls)
    }

    @Test
    fun `given blank identifiers or metadata when content is created then validation fails`() {
        assertThrows<IllegalArgumentException> { validContent(id = " ") }
        assertThrows<IllegalArgumentException> { validContent(locale = " ") }
        assertThrows<IllegalArgumentException> { validContent(country = " ") }
        assertThrows<IllegalArgumentException> { validContent(authorId = " ") }
    }

    @Test
    fun `given blank image url when content is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            validContent(imageUrls = listOf("https://cdn.example/photo.jpg", " "))
        }
    }

    @Test
    fun `given invalid context message when context is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            ContextMessage(contentId = " ", authorId = "user-1", text = "message")
        }
        assertThrows<IllegalArgumentException> {
            ContextMessage(contentId = "message-1", authorId = " ", text = "message")
        }
        assertThrows<IllegalArgumentException> {
            ContextMessage(contentId = "message-1", authorId = "user-1", text = " ")
        }
    }

    @Test
    fun `given ordered context when content is created then message order is retained`() {
        val first = ContextMessage("message-1", "user-1", "first")
        val second = ContextMessage("message-2", "user-2", "second")

        val content = validContent(conversationContext = listOf(first, second))

        assertEquals(listOf(first, second), content.conversationContext)
    }

    @Test
    fun `content types expose all supported moderation inputs`() {
        assertEquals(
            setOf(
                ContentType.MESSAGE,
                ContentType.PROFILE_DESCRIPTION,
                ContentType.PHOTO,
                ContentType.REPORT
            ),
            ContentType.entries.toSet()
        )
    }

    private fun validContent(
        id: String = "content-1",
        locale: String? = null,
        country: String? = null,
        authorId: String? = null,
        imageUrls: List<String> = emptyList(),
        conversationContext: List<ContextMessage> = emptyList()
    ): ModerationContent = ModerationContent(
        id = id,
        type = ContentType.MESSAGE,
        text = "message",
        imageUrls = imageUrls,
        locale = locale,
        country = country,
        authorId = authorId,
        conversationContext = conversationContext
    )
}
