package com.tinder.clone.moderation.infrastructure.provider

import com.sun.net.httpserver.HttpServer
import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.common.enums.LlmLabel
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ContextMessage
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.signals.AppliedPolicy
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderAdaptersTest {
    @Test
    fun `missing provider keys are provider failures rather than invalid user payloads`() {
        val mapper = jacksonObjectMapper()
        val content = ModerationContent("content", ContentType.MESSAGE, "hello")

        val openAiError = kotlin.test.assertFailsWith<ProviderException> {
            OpenAiModerationAdapter(OpenAiModerationProperties(), mapper).classify(content)
        }
        assertEquals("openai", openAiError.provider)

        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }
        val geminiError = kotlin.test.assertFailsWith<ProviderException> {
            GeminiLlmAdapter(GeminiProperties(), mapper).analyzeContent(
                LlmAnalysisRequest(
                    content,
                    emptyList(),
                    ClassificationResult("openai", "model", null, categories, false, 0),
                    AppliedPolicy("policy", null, null)
                )
            )
        }
        assertEquals("gemini", geminiError.provider)
    }
    @Test
    fun `OpenAI adapter sends multimodal input and preserves unsupported domain categories`() {
        var requestBody = ""
        withServer(
            response = """{
              "id":"modr-test","model":"omni-moderation-2024-09-26","results":[{
                "flagged":true,
                "categories":{"harassment":true,"violence":false,"violence/graphic":false},
                "category_scores":{"harassment":0.91,"violence":0.1,"violence/graphic":0.2}
              }]}
            """.trimIndent(),
            capture = { requestBody = it }
        ) { baseUrl ->
            val adapter = OpenAiModerationAdapter(
                OpenAiModerationProperties("test-key", baseUrl, "omni-moderation-latest", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                jacksonObjectMapper()
            )
            val result = adapter.classify(
                ModerationContent("content", ContentType.MESSAGE, "hello", listOf("https://cdn.example/photo.jpg"))
            )

            assertEquals("openai", result.provider)
            assertEquals("omni-moderation-2024-09-26", result.modelSnapshot)
            assertEquals(0.91, result.categories.getValue(ModerationCategory.HARASSMENT).score?.value)
            assertFalse(result.categories.getValue(ModerationCategory.SPAM).supported)
            assertTrue(result.flagged)
            assertTrue(requestBody.contains("\"type\":\"text\""))
            assertTrue(requestBody.contains("\"type\":\"image_url\""))
        }
    }

    @Test
    fun `Gemini adapter receives context without sending raw author identifiers`() {
        var requestBody = ""
        withServer(
            response = """{"candidates":[{"content":{"parts":[{"text":"{\"label\":\"SAFE\",\"confidence\":0.94}"}]}}]}""",
            capture = { requestBody = it }
        ) { baseUrl ->
            val adapter = GeminiLlmAdapter(
                GeminiProperties("test-key", baseUrl, "gemini-3.8-flash", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                jacksonObjectMapper()
            )
            val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() } + mapOf(
                ModerationCategory.HARASSMENT to CategoryScore.supported(0.6, false)
            )
            val content = ModerationContent(
                "current-id",
                ContentType.MESSAGE,
                "I am quoting the villain",
                locale = "en",
                authorId = "private-subject-id",
                conversationContext = listOf(
                    ContextMessage("previous-id", "private-other-id", "It is a quote from a book")
                )
            )
            val result = adapter.analyzeContent(
                LlmAnalysisRequest(
                    content,
                    emptyList(),
                    ClassificationResult("openai", "omni-moderation-latest", "snapshot", categories, false, 1),
                    AppliedPolicy("policy-v1", ContentType.MESSAGE, "en")
                )
            )

            assertEquals(LlmLabel.SAFE, result.label)
            assertEquals(0.94, result.confidence)
            assertTrue(requestBody.contains("It is a quote from a book"))
            assertTrue(requestBody.contains("OTHER:"))
            assertFalse(requestBody.contains("private-subject-id"))
            assertFalse(requestBody.contains("private-other-id"))
            assertFalse(requestBody.contains("previous-id"))
        }
    }

    private fun withServer(response: String, capture: (String) -> Unit, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            capture(exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
