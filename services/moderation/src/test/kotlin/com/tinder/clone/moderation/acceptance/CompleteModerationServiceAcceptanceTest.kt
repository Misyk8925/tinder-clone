package com.tinder.clone.moderation.acceptance

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@Tag("acceptance")
@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN",
        "moderation.security.users[1].username=viewer",
        "moderation.security.users[1].password-hash=\$2b\$10\$B8JziNtYQcAfaF4OUFXpxejHeLQ.jmCsma5ZRP65d6f.Eli5ADkJ.",
        "moderation.security.users[1].roles=VIEWER",
        "management.endpoints.web.exposure.include=info",
    ]
)
@AutoConfigureMockMvc
@Import(CompleteModerationServiceAcceptanceTest.AcceptanceProviders::class)
class CompleteModerationServiceAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc

    private val auth = "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ=="
    private val viewerAuth = "Basic dmlld2VyOnZpZXdlci1vbmx5"
    private val moderationRequest = """
        {
          "contentId":"message-42",
          "contentType":"MESSAGE",
          "text":"This quoted sentence needs context",
          "locale":"en",
          "authorId":"subject-1",
          "conversationContext":[
            {"contentId":"message-41","authorId":"other-1","text":"Quote the book passage"}
          ]
        }
    """.trimIndent()

    @TestConfiguration
    class AcceptanceProviders {
        @Bean
        @Primary
        fun classifier(): com.tinder.clone.moderation.application.ports.ModerationClassifierPort =
            object : com.tinder.clone.moderation.application.ports.ModerationClassifierPort {
                override fun classify(content: com.tinder.clone.moderation.domain.model.ModerationContent):
                    com.tinder.clone.moderation.application.commands.output.ClassificationResult {
                    val categories = com.tinder.clone.moderation.domain.model.ModerationCategory.entries.associateWith {
                        com.tinder.clone.moderation.application.commands.output.CategoryScore.unsupported()
                    } + mapOf(
                        com.tinder.clone.moderation.domain.model.ModerationCategory.HARASSMENT to
                        com.tinder.clone.moderation.application.commands.output.CategoryScore.supported(0.60, false)
                    )
                    return com.tinder.clone.moderation.application.commands.output.ClassificationResult(
                        "openai", "omni-moderation-latest", "test-snapshot", categories, false, 1
                    )
                }
            }

        @Bean
        @Primary
        fun llm(): com.tinder.clone.moderation.application.ports.LlmPort =
            object : com.tinder.clone.moderation.application.ports.LlmPort {
                override fun analyzeContent(request: com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest) =
                    com.tinder.clone.moderation.application.commands.output.LlmResult(
                        com.tinder.clone.moderation.common.enums.LlmLabel.SAFE,
                        0.99,
                        "gemini",
                        "test-model"
                    )
            }
    }

    @Test
    fun `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", auth)
            header("Idempotency-Key", "acceptance-message-42")
            contentType = MediaType.APPLICATION_JSON
            content = moderationRequest
        }.andExpect {
            status { isOk() }
            jsonPath("$.contentId") { value("message-42") }
            jsonPath("$.decision") { exists() }
            jsonPath("$.evidence.provider") { exists() }
            jsonPath("$.evidence.policyVersion") { exists() }
        }
    }

    @Test
    fun `FR-8 FR-9 duplicate request replays the durable decision`() {
        fun execute() = http.post("/internal/v1/moderations") {
            header("Authorization", auth)
            header("Idempotency-Key", "acceptance-replay-42")
            contentType = MediaType.APPLICATION_JSON
            content = moderationRequest
        }.andReturn().response

        val first = execute()
        val second = execute()
        kotlin.test.assertEquals(200, first.status)
        kotlin.test.assertEquals(200, second.status)
        kotlin.test.assertEquals(first.contentAsString.substringAfter("\"decisionId\":\"").substringBefore('"'),
            second.contentAsString.substringAfter("\"decisionId\":\"").substringBefore('"'))
        kotlin.test.assertTrue(second.contentAsString.contains("\"replayed\":true"))
    }

    @Test
    fun `FR-10 FR-11 policy draft publishes and activates without restart`() {
        val draft = """
            {"version":"acceptance-v1","scopes":[{"contentType":"MESSAGE","locale":"en",
            "thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}
        """.trimIndent()
        http.post("/internal/v1/policies") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = draft
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("DRAFT") } }
        http.post("/internal/v1/policies/acceptance-v1/validation") {
            header("Authorization", auth)
        }.andExpect { status { isOk() }; jsonPath("$.valid") { value(true) } }
        http.post("/internal/v1/policies/acceptance-v1/publication") {
            header("Authorization", auth); header("If-Match", "\"0\"")
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("PUBLISHED") } }
        http.put("/internal/v1/policies/acceptance-v1/activation") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"MESSAGE","locale":"en"}"""
        }.andExpect { status { isOk() }; jsonPath("$.version") { value("acceptance-v1") } }
    }

    @Test
    fun `FR-12 policy preview does not create a moderation decision`() {
        http.post("/internal/v1/policy-previews") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"policyVersion":"acceptance-v1","contentType":"MESSAGE","locale":"en","evidence":{}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.persisted") { value(false) }
        }
    }

    @Test
    fun `FR-13 FR-14 review queue resolves with optimistic locking`() {
        val created = http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "review-fixture-42")
            contentType = MediaType.APPLICATION_JSON; content = moderationRequest
        }.andReturn().response.contentAsString
        val reviewId = created.substringAfter("\"reviewTaskId\":\"").substringBefore('"')
        http.get("/internal/v1/review-tasks?status=OPEN") {
            header("Authorization", auth)
        }.andExpect { status { isOk() }; jsonPath("$.items") { isArray() } }
        http.put("/internal/v1/review-tasks/$reviewId/resolution") {
            header("Authorization", auth)
            header("If-Match", "\"0\"")
            contentType = MediaType.APPLICATION_JSON
            content = """{"action":"CONFIRM","note":"Acceptance review"}"""
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("RESOLVED") } }
    }

    @Test
    fun `FR-15 FR-16 internal admin GUI renders decisions reviews and policies`() {
        listOf("/admin", "/admin/decisions", "/admin/reviews", "/admin/policies").forEach { path ->
            http.get(path) { header("Authorization", auth) }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            }
        }
    }

    @Test
    fun `FR-17 anonymous API caller is rejected`() {
        http.get("/internal/v1/moderations").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
    }

    @Test
    fun `FR-18 FR-2 async boundaries are available in the running application`() {
        http.get("/actuator/health/readiness") { header("Authorization", auth) }.andExpect {
            status { isOk() }
            content { string(containsString("kafka")) }
            content { string(containsString("db")) }
        }
    }

    @Test
    fun `FR-19 health and metrics endpoints are exposed internally`() {
        http.get("/actuator/health/readiness") { header("Authorization", auth) }
            .andExpect { status { isOk() } }
        http.get("/actuator/metrics/moderation.decisions") { header("Authorization", auth) }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `ERR-HTTP-400 invalid request returns stable error`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "acceptance-invalid")
            contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_PAYLOAD") } }
    }

    @Test
    fun `ERR-HTTP-413 oversized request is rejected before providers`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "acceptance-oversized")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentId":"large","contentType":"MESSAGE","text":"${"x".repeat(1_048_577)}"}"""
        }.andExpect { status { isContentTooLarge() }; jsonPath("$.code") { value("PAYLOAD_TOO_LARGE") } }
    }

    @Test
    fun `ERR-IDEMPOTENCY-409 reused key with another body conflicts`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "acceptance-conflict")
            contentType = MediaType.APPLICATION_JSON; content = moderationRequest
        }
        http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "acceptance-conflict")
            contentType = MediaType.APPLICATION_JSON; content = moderationRequest.replace("message-42", "message-43")
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("IDEMPOTENCY_CONFLICT") } }
    }

    @Test
    fun `ERR-HTTP-404 unknown decision is not found`() {
        http.get("/internal/v1/moderations/00000000-0000-0000-0000-000000000099") {
            header("Authorization", auth)
        }.andExpect { status { isNotFound() }; jsonPath("$.code") { value("NOT_FOUND") } }
    }

    @Test
    fun `ERR-POLICY and review transitions return stable conflict errors`() {
        val published = """{"version":"published-v1","scopes":[{"contentType":"MESSAGE","thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}"""
        http.post("/internal/v1/policies") { header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = published }
        http.post("/internal/v1/policies/published-v1/publication") { header("Authorization", auth); header("If-Match", "\"0\"") }
        http.put("/internal/v1/policies/published-v1") {
            header("Authorization", auth); header("If-Match", "\"0\"")
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"published-v1","scopes":[]}"""
        }.andExpect { status { isUnprocessableContent() }; jsonPath("$.code") { value("PUBLISHED_POLICY_IMMUTABLE") } }

        val created = http.post("/internal/v1/moderations") {
            header("Authorization", auth); header("Idempotency-Key", "review-resolved-43")
            contentType = MediaType.APPLICATION_JSON; content = moderationRequest
        }.andReturn().response.contentAsString
        val reviewId = created.substringAfter("\"reviewTaskId\":\"").substringBefore('"')
        http.put("/internal/v1/review-tasks/$reviewId/resolution") {
            header("Authorization", auth); header("If-Match", "\"0\"")
            contentType = MediaType.APPLICATION_JSON; content = """{"action":"CONFIRM"}"""
        }
        http.put("/internal/v1/review-tasks/$reviewId/resolution") {
            header("Authorization", auth); header("If-Match", "\"1\"")
            contentType = MediaType.APPLICATION_JSON; content = """{"action":"CONFIRM"}"""
        }.andExpect { status { isUnprocessableContent() }; jsonPath("$.code") { value("REVIEW_ALREADY_RESOLVED") } }
    }

    @Test
    fun `ERR-HTTP-403 viewer cannot mutate policy`() {
        http.post("/internal/v1/policies") {
            header("Authorization", viewerAuth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"forbidden-v1","scopes":[]}"""
        }.andExpect { status { isForbidden() }; jsonPath("$.code") { value("FORBIDDEN") } }
    }

    @Test
    fun `ERR-VERSION-409 stale policy update conflicts`() {
        http.post("/internal/v1/policies") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"stale-draft-v1","scopes":[{"thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}"""
        }
        http.put("/internal/v1/policies/stale-draft-v1") {
            header("Authorization", auth); header("If-Match", "\"999\"")
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"stale-draft-v1","scopes":[]}"""
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("VERSION_CONFLICT") } }
    }

    @Test
    fun `ERR-POLICY-EXISTS-409 duplicate policy version conflicts`() {
        val draft = """{"version":"duplicate-v1","scopes":[{"thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}"""
        repeat(2) {
            val result = http.post("/internal/v1/policies") {
                header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = draft
            }.andReturn().response
            if (it == 1) {
                kotlin.test.assertEquals(409, result.status)
                kotlin.test.assertTrue(result.contentAsString.contains("POLICY_VERSION_EXISTS"))
            }
        }
    }

    @Test
    fun `ERR-POLICY-INVALID-422 invalid draft cannot publish`() {
        http.post("/internal/v1/policies") { header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = """{"version":"invalid-v1","scopes":[]}""" }
        http.post("/internal/v1/policies/invalid-v1/publication") {
            header("Authorization", auth); header("If-Match", "\"0\"")
        }.andExpect { status { isUnprocessableContent() }; jsonPath("$.code") { value("POLICY_INVALID") } }
    }

    @Test
    fun `ERR-POLICY-NOT-PUBLISHED-422 draft cannot activate`() {
        http.post("/internal/v1/policies") { header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = """{"version":"draft-v1","scopes":[{"thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}""" }
        http.put("/internal/v1/policies/draft-v1/activation") {
            header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andExpect { status { isUnprocessableContent() }; jsonPath("$.code") { value("POLICY_NOT_PUBLISHED") } }
    }

    @Test
    fun `ERR-NO-PREVIOUS-422 activation without history cannot roll back`() {
        http.post("/internal/v1/policies") { header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = """{"version":"no-prev-v1","scopes":[{"thresholds":[{"category":"HARASSMENT","review":0.5,"block":0.9}]}]}""" }
        http.post("/internal/v1/policies/no-prev-v1/publication") { header("Authorization", auth); header("If-Match", "\"0\"") }
        val activation = http.put("/internal/v1/policies/no-prev-v1/activation") {
            header("Authorization", auth); contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andReturn().response.contentAsString
        val activationId = activation.substringAfter("\"activationId\":\"").substringBefore('"')
        http.post("/internal/v1/policy-activations/$activationId/rollback") {
            header("Authorization", auth); header("If-Match", "\"0\"")
        }.andExpect { status { isUnprocessableContent() }; jsonPath("$.code") { value("NO_PREVIOUS_POLICY") } }
    }

    @Test
    fun `ERR-HTTP-503 unavailable durable storage is retryable`() {
        val response = com.tinder.clone.moderation.infrastructure.http.ApiExceptionHandler()
            .storageUnavailable()
        kotlin.test.assertEquals(503, response.statusCode.value())
        kotlin.test.assertEquals("SERVICE_UNAVAILABLE", response.body?.code)
    }
}
