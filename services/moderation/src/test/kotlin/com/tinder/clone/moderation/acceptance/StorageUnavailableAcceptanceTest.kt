package com.tinder.clone.moderation.acceptance

import com.tinder.clone.moderation.application.service.DecisionSaveResult
import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import com.tinder.clone.moderation.application.service.ReviewAction
import com.tinder.clone.moderation.application.service.ReviewStatus
import com.tinder.clone.moderation.application.service.ReviewTask
import com.tinder.clone.moderation.application.service.StoredModerationDecision
import com.tinder.clone.moderation.infrastructure.persistence.DurableStorageException
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
import org.springframework.test.web.servlet.post
import java.util.UUID

@Tag("acceptance")
@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN"
    ]
)
@AutoConfigureMockMvc
@Import(StorageUnavailableAcceptanceTest.FailingStoreConfig::class)
class StorageUnavailableAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc

    @TestConfiguration
    class FailingStoreConfig {
        @Bean
        @Primary
        fun failingStore(): ModerationDecisionStore = object : ModerationDecisionStore {
            override fun findByIdempotencyKey(key: String) = null
            override fun saveOrGet(decision: StoredModerationDecision): DecisionSaveResult =
                throw DurableStorageException(IllegalStateException("database is down"))
            override fun get(id: UUID) = null
            override fun list() = emptyList<com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto>()
            override fun listReviews(status: ReviewStatus?) = emptyList<ReviewTask>()
            override fun getReview(id: UUID) = null
            override fun resolveReview(id: UUID, expectedVersion: Long, action: ReviewAction, note: String?, actor: String) =
                throw DurableStorageException(IllegalStateException("database is down"))
        }
    }

    @Test
    fun `ERR-HTTP-503 unavailable durable storage is retryable`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ==")
            header("Idempotency-Key", "storage-down-42")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentId":"message-503","contentType":"MESSAGE","text":"quoted film line"}"""
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
            jsonPath("$.retryable") { value(true) }
            header { exists("Retry-After") }
        }
    }
}
