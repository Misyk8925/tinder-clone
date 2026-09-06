package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.policy.PolicyScopeDefinition
import com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry
import com.tinder.clone.moderation.application.service.DecisionSaveResult
import com.tinder.clone.moderation.application.service.StoredModerationDecision
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.policy.CategoryThreshold
import com.tinder.clone.moderation.infrastructure.http.CategoryScoreDto
import com.tinder.clone.moderation.infrastructure.http.ContextMessageDto
import com.tinder.clone.moderation.infrastructure.http.EvidenceDto
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPersistenceIntegrationTest {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private val mapper = jacksonObjectMapper()
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun startDatabase() {
        postgres.start()
        Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).load().migrate()
        jdbc = JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @Test
    fun `policy and activation survive registry restart without losing thresholds`() {
        val clock = Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC)
        val first = RuntimePolicyRegistry(clock, JdbcPolicyStateStore(jdbc, mapper))
        first.create(
            "restart-v1", "restart proof",
            listOf(PolicyScopeDefinition(ContentType.MESSAGE, "en", mapOf(
                ModerationCategory.HARASSMENT to CategoryThreshold(0.4, 0.8)
            )))
        )
        first.publish("restart-v1", 0)
        first.activate("restart-v1", ContentType.MESSAGE, "en")

        val afterRestart = RuntimePolicyRegistry(clock, JdbcPolicyStateStore(jdbc, mapper))
        val restored = afterRestart.get("restart-v1")
        assertEquals(PolicyScopeDefinition(ContentType.MESSAGE, "en", mapOf(
            ModerationCategory.HARASSMENT to CategoryThreshold(0.4, 0.8)
        )), restored.scopes.single())
        assertEquals("restart-v1", afterRestart.policyFor(
            ModerationContent("message", ContentType.MESSAGE, "hello", locale = "en")
        ).version)
    }

    @Test
    fun `decision request context and complete evidence survive a database round trip`() {
        val store = JdbcModerationDecisionStore(jdbc, mapper)
        val candidate = decision("round-trip-key", "a".repeat(64))
        assertEquals(candidate, (store.saveOrGet(candidate) as DecisionSaveResult.Created).decision)

        val restored = assertNotNull(store.findByIdempotencyKey("round-trip-key"))
        assertEquals(candidate.request.copy(text = "caf\u00e9"), restored.request)
        assertEquals(candidate.response, restored.response)
        assertFalse(restored.response.replayed)
    }

    @Test
    fun `twenty concurrent duplicates create exactly one durable decision`() {
        val store = JdbcModerationDecisionStore(jdbc, mapper)
        val pool = Executors.newFixedThreadPool(20)
        try {
            val results = pool.invokeAll((1..20).map { index -> Callable {
                store.saveOrGet(decision("concurrent-key", "b".repeat(64), UUID.randomUUID()))
            }}).map { future -> when (val result = future.get()) {
                is DecisionSaveResult.Created -> result.decision.response.decisionId
                is DecisionSaveResult.Existing -> result.decision.response.decisionId
            }}
            assertEquals(1, results.distinct().size)
            assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM moderation_decision WHERE idempotency_key = 'concurrent-key'", Int::class.java
            ))
        } finally {
            pool.shutdownNow()
        }
    }

    private fun decision(key: String, hash: String, id: UUID = UUID.randomUUID()): StoredModerationDecision {
        val created = Instant.parse("2026-09-06T10:01:00Z")
        val request = ModerationRequestDto(
            "message-1", ContentType.MESSAGE, "cafe\u0301", listOf("https://example.test/image.jpg"),
            "en", "AT", "subject", listOf(ContextMessageDto("previous", "other", "book quote"))
        )
        val evidence = EvidenceDto(
            "openai", "omni-moderation-latest", "snapshot",
            listOf(CategoryScoreDto("HARASSMENT", 0.12, false, true)),
            listOf(mapOf("type" to "ACCOUNT_AGE", "attributes" to mapOf("days" to 42))),
            mapOf("provider" to "gemini", "model" to "gemini-test", "label" to "SAFE", "confidence" to 0.98),
            listOf(mapOf("ruleId" to "threshold:HARASSMENT", "reason" to "CATEGORY_REVIEW")),
            "unconfigured", mapOf("contentType" to null, "locale" to null)
        )
        return StoredModerationDecision(
            key, hash, request,
            ModerationResponseDto(id, request.contentId, "HOLD", "POLICY_NOT_CONFIGURED", null, evidence, createdAt = created)
        )
    }
}
