package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import kotlin.system.measureNanoTime

class OpenAiModerationAdapter(
    private val properties: OpenAiModerationProperties,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient = ProviderRestClientFactory.create(
        properties.baseUrl,
        properties.connectTimeout,
        properties.readTimeout
    )
) : ModerationClassifierPort {

    override fun classify(content: ModerationContent): ClassificationResult {
        if (properties.apiKey.isBlank()) {
            throw ProviderException("openai", "OpenAI moderation API key is not configured")
        }
        var responseBody: String? = null
        val elapsedNanos = try {
            measureNanoTime {
                responseBody = restClient.post()
                    .uri("/v1/moderations")
                    .header("Authorization", "Bearer ${properties.apiKey}")
                    .body(mapOf("model" to properties.model, "input" to providerInput(content)))
                    .retrieve()
                    .body(String::class.java)
            }
        } catch (error: Exception) {
            throw ProviderException("openai", "OpenAI moderation request failed", error)
        }

        try {
            val root = objectMapper.readTree(requireNotNull(responseBody) { "OpenAI returned an empty response" })
            val result = root.path("results").firstOrNull()
                ?: throw ProviderException("openai", "OpenAI response contains no moderation result")
            val scores = result.path("category_scores")
            val flags = result.path("categories")
            val categories = ModerationCategory.entries.associateWith { category ->
                mapCategory(category, scores, flags)
            }
            return ClassificationResult(
                provider = "openai",
                model = properties.model,
                modelSnapshot = root.path("model").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
                categories = categories,
                flagged = categories.values.any { it.flagged },
                latencyMs = elapsedNanos / 1_000_000
            )
        } catch (error: ProviderException) {
            throw error
        } catch (error: Exception) {
            throw ProviderException("openai", "OpenAI moderation response is malformed", error)
        }
    }

    private fun providerInput(content: ModerationContent): List<Map<String, Any>> = buildList {
        content.text?.let { add(mapOf("type" to "text", "text" to it)) }
        content.imageUrls.forEach { imageUrl ->
            add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
        }
    }

    private fun mapCategory(
        category: ModerationCategory,
        scores: tools.jackson.databind.JsonNode,
        flags: tools.jackson.databind.JsonNode
    ): CategoryScore {
        val providerNames = when (category) {
            ModerationCategory.HARASSMENT -> listOf("harassment")
            ModerationCategory.HARASSMENT_THREATENING -> listOf("harassment/threatening")
            ModerationCategory.HATE -> listOf("hate")
            ModerationCategory.HATE_THREATENING -> listOf("hate/threatening")
            ModerationCategory.SEXUAL_CONTENT -> listOf("sexual")
            ModerationCategory.SEXUAL_MINORS -> listOf("sexual/minors")
            ModerationCategory.SELF_HARM -> listOf("self-harm", "self-harm/intent", "self-harm/instructions")
            ModerationCategory.VIOLENCE -> listOf("violence", "violence/graphic")
            else -> emptyList()
        }
        if (providerNames.isEmpty() || providerNames.none { scores.has(it) && !scores.path(it).isNull }) {
            return CategoryScore.unsupported()
        }
        val score = providerNames.mapNotNull { name ->
            scores.path(name).takeUnless { it.isMissingNode || it.isNull }?.asDouble()
        }.maxOrNull() ?: return CategoryScore.unsupported()
        val flagged = providerNames.any { name -> flags.path(name).asBoolean(false) }
        return CategoryScore.supported(score, flagged)
    }
}
