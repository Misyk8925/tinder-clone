package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.commands.output.LlmResult
import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.common.enums.LlmLabel
import com.tinder.clone.moderation.domain.model.ContextMessage
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

class GeminiLlmAdapter(
    private val properties: GeminiProperties,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient = ProviderRestClientFactory.create(
        properties.baseUrl,
        properties.connectTimeout,
        properties.readTimeout
    )
) : LlmPort {

    override fun analyzeContent(request: LlmAnalysisRequest): LlmResult {
        if (properties.apiKey.isBlank()) {
            throw ProviderException("gemini", "Gemini API key is not configured")
        }
        val response = try {
            restClient.post()
                .uri("/v1beta/models/{model}:generateContent", properties.model)
                .header("x-goog-api-key", properties.apiKey)
                .body(providerRequest(request))
                .retrieve()
                .body(String::class.java)
        } catch (error: Exception) {
            throw ProviderException("gemini", "Gemini adjudication request failed", error)
        }

        try {
            val root = objectMapper.readTree(requireNotNull(response) { "Gemini returned an empty response" })
            val text = root.path("candidates").firstOrNull()
                ?.path("content")?.path("parts")?.firstOrNull()?.path("text")?.stringValue()
                ?: throw ProviderException("gemini", "Gemini response contains no structured output")
            val structured = objectMapper.readTree(text)
            return LlmResult(
                label = LlmLabel.valueOf(structured.path("label").stringValue()),
                confidence = structured.path("confidence").asDouble(-1.0),
                provider = "gemini",
                model = properties.model
            ).also { require(it.confidence in 0.0..1.0) { "Gemini confidence is outside [0,1]" } }
        } catch (error: ProviderException) {
            throw error
        } catch (error: Exception) {
            throw ProviderException("gemini", "Gemini structured output is malformed", error)
        }
    }

    private fun providerRequest(request: LlmAnalysisRequest): Map<String, Any> = mapOf(
        "systemInstruction" to mapOf(
            "parts" to listOf(mapOf("text" to SYSTEM_INSTRUCTION))
        ),
        "contents" to listOf(
            mapOf("role" to "user", "parts" to listOf(mapOf("text" to prompt(request))))
        ),
        "generationConfig" to mapOf(
            "temperature" to 0,
            "responseMimeType" to "application/json",
            "responseJsonSchema" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "required" to listOf("label", "confidence"),
                "properties" to mapOf(
                    "label" to mapOf("type" to "string", "enum" to LlmLabel.entries.map { it.name }),
                    "confidence" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1)
                )
            )
        )
    )

    private fun prompt(request: LlmAnalysisRequest): String = buildString {
        appendLine("Decide whether the CURRENT message itself is safe, hateful, or harassment.")
        appendLine("Context may be a quote, news, fiction, negation, or support conversation. Do not attribute context text to the current author.")
        appendLine("Policy version: ${request.appliedPolicy.version}")
        appendLine("Content type: ${request.content.type.name}")
        appendLine("Locale: ${request.content.locale ?: "unknown"}")
        appendLine("Classifier scores:")
        request.classifierResult.categories.forEach { (category, value) ->
            appendLine("- ${category.name}: ${value.score?.value ?: "unsupported"}; flagged=${value.flagged}")
        }
        appendLine("Application signals: ${request.applicationSignals.joinToString { it.type.name }}")
        appendLine("Conversation context:")
        request.content.conversationContext.forEach { appendLine(renderContext(it, request.content.authorId)) }
        appendLine("CURRENT: ${request.content.text}")
    }

    private fun renderContext(message: ContextMessage, subjectAuthorId: String?): String {
        val role = if (subjectAuthorId != null && message.authorId == subjectAuthorId) "SUBJECT" else "OTHER"
        return "$role: ${message.text}"
    }

    private companion object {
        const val SYSTEM_INSTRUCTION =
            "You are a safety adjudicator. Return only the requested JSON. Evaluate authorship and conversational context carefully."
    }
}
