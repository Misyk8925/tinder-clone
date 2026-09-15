package com.tinder.clone.moderation.acceptance

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.tinder.clone.moderation.application.security.InMemoryLoginAttemptStore
import com.tinder.clone.moderation.application.security.LoginAttemptService
import com.tinder.clone.moderation.application.security.RawContentRetention
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val AUTH = "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ=="
private val SECURITY_USERS = arrayOf(
    "moderation.security.users[0].username=policy-admin",
    "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
    "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN"
)

@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN",
        "management.endpoint.health.probes.enabled=true"
    ]
)
@AutoConfigureMockMvc
class FallbackWithoutKeysAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc

    @Test
    fun `blank provider keys HOLD clean and keyword-matched text without semantic decisions`() {
        http.post("/internal/v1/moderations") {
            header("Authorization", AUTH)
            header("Idempotency-Key", "fallback-clean-42")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentId":"bio-clean","contentType":"PROFILE_DESCRIPTION","text":"Coffee and a long walk"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("HOLD") }
            jsonPath("$.reason") { value("CLASSIFIER_CATEGORY_UNSUPPORTED") }
            jsonPath("$.evidence.provider") { value("fallback") }
        }
        http.post("/internal/v1/moderations") {
            header("Authorization", AUTH)
            header("Idempotency-Key", "fallback-hate-42")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentId":"bio-hate","contentType":"PROFILE_DESCRIPTION","text":"I hate all outsiders and they should die"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("HOLD") }
            jsonPath("$.reason") { value("CLASSIFIER_CATEGORY_UNSUPPORTED") }
        }
    }
}

class LoginLockoutTest {
    @Test
    fun `five failed logins lock the account for fifteen minutes`() {
        val clock = MutableClock(Instant.parse("2026-09-15T12:00:00Z"))
        val attempts = LoginAttemptService(InMemoryLoginAttemptStore(), clock, 5, Duration.ofMinutes(15))
        repeat(5) { attempts.recordFailure("policy-admin") }
        assertTrue(attempts.isLocked("policy-admin"))
        clock.instant = Instant.parse("2026-09-15T12:14:59Z")
        assertTrue(attempts.isLocked("policy-admin"))
        clock.instant = Instant.parse("2026-09-15T12:15:01Z")
        kotlin.test.assertFalse(attempts.isLocked("policy-admin"))
        attempts.recordFailure("policy-admin")
        kotlin.test.assertFalse(attempts.isLocked("policy-admin"))
    }

    @Test
    fun `five concurrent failed logins cannot lose increments`() {
        val clock = MutableClock(Instant.parse("2026-09-15T12:00:00Z"))
        val attempts = LoginAttemptService(InMemoryLoginAttemptStore(), clock, 5, Duration.ofMinutes(15))
        val pool = Executors.newFixedThreadPool(5)
        try {
            pool.invokeAll((1..5).map { Callable { attempts.recordFailure("policy-admin") } })
                .forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertTrue(attempts.isLocked("policy-admin"))
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant
    }
}

class RawContentRetentionTest {
    @Test
    fun `expired raw content is removed while aggregated evidence remains`() {
        data class Record(var text: String?, var evidence: String, val expiresAt: Instant)
        val created = Instant.parse("2026-06-01T00:00:00Z")
        val records = mutableListOf(
            Record("secret conversation", "scores", created.plus(Duration.ofDays(90))),
            Record("still fresh", "scores", created.plus(Duration.ofDays(91)))
        )
        val clock = Clock.fixed(created.plus(Duration.ofDays(90)).plusSeconds(1), ZoneOffset.UTC)
        val job = RawContentRetention(
            expire = { now ->
                var count = 0
                records.forEach { record ->
                    if (!record.expiresAt.isAfter(now) && record.text != null) {
                        record.text = null
                        count += 1
                    }
                }
                count
            },
            clock = clock
        )
        assertEquals(1, job.purgeExpired())
        kotlin.test.assertNull(records[0].text)
        assertEquals("scores", records[0].evidence)
        assertEquals("still fresh", records[1].text)
    }
}

class SensitiveLogTest {
    @Test
    fun `application logs do not contain raw content or credentials`() {
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        val logger = LoggerFactory.getLogger("ROOT") as Logger
        logger.addAppender(appender)
        try {
            val executions = com.tinder.clone.moderation.application.service.ModerationExecutionService(
                input = object : com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort {
                    override fun handle(contentCmd: com.tinder.clone.moderation.application.commands.input.ContentCmd) =
                        com.tinder.clone.moderation.application.usecase.ModerateContentUsecase(
                            com.tinder.clone.moderation.infrastructure.provider.FallbackClassifier(),
                            com.tinder.clone.moderation.infrastructure.provider.FallbackLlmAdapter(),
                            com.tinder.clone.moderation.domain.ModerationDomainService(
                                com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry()
                            ),
                            com.tinder.clone.moderation.application.service.EvidenceBuilder(),
                            com.tinder.clone.moderation.application.service.PreModerationProcessor()
                        ).handle(contentCmd)
                },
                objectMapper = tools.jackson.module.kotlin.jacksonObjectMapper(),
                store = com.tinder.clone.moderation.application.service.InMemoryModerationDecisionStore()
            )
            executions.execute(
                "log-secret-42",
                com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto(
                    "message-secret",
                    com.tinder.clone.moderation.domain.model.ContentType.MESSAGE,
                    "SECRET_CONTENT_MARKER_XYZ",
                    authorId = "sk-test-secret-key"
                )
            )
            val messages = appender.list.joinToString("\n") { it.formattedMessage }
            kotlin.test.assertFalse(messages.contains("SECRET_CONTENT_MARKER_XYZ"))
            kotlin.test.assertFalse(messages.contains("sk-test-secret-key"))
        } finally {
            logger.detachAppender(appender)
        }
    }
}

@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN"
    ]
)
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class AdminBrowserAndSecurityAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc
    @Autowired
    private lateinit var environment: org.springframework.core.env.Environment

    @Test
    fun `anonymous browser is redirected to login and session cookie is HttpOnly SameSite Lax`() {
        http.get("/admin").andExpect {
            status { is3xxRedirection() }
            header { string("Location", org.hamcrest.Matchers.containsString("/login")) }
        }
        val login = http.get("/login").andReturn().response
        assertEquals(200, login.status)
        assertTrue(login.contentAsString.contains("name=\"_csrf\""))
        val authenticated = http.post("/login") {
            param("username", "policy-admin")
            param("password", "test-only")
            with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        }.andExpect {
            status { is3xxRedirection() }
        }.andReturn()
        kotlin.test.assertNotNull(authenticated.request.session)
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.http-only"))
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"))
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.secure"))
    }
}

class PolicyAuditTest {
    @Test
    fun `NFR-10 policy mutations write an audit record`() {
        val store = com.tinder.clone.moderation.application.policy.InMemoryPolicyStateStore()
        val registry = com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry(store = store)
        registry.create(
            "audit-v1",
            "audit",
            listOf(
                com.tinder.clone.moderation.application.policy.PolicyScopeDefinition(
                    com.tinder.clone.moderation.domain.model.ContentType.MESSAGE,
                    "en",
                    mapOf(
                        com.tinder.clone.moderation.domain.model.ModerationCategory.HARASSMENT to
                            com.tinder.clone.moderation.domain.policy.CategoryThreshold(0.5, 0.9)
                    )
                )
            ),
            "policy-admin"
        )
        registry.publish("audit-v1", 0, "policy-admin")
        registry.activate("audit-v1", com.tinder.clone.moderation.domain.model.ContentType.MESSAGE, "en", "policy-admin")
        val actions = store.auditLog().map { it.action }
        assertTrue(actions.contains("CREATE_POLICY"))
        assertTrue(actions.contains("PUBLISH_POLICY"))
        assertTrue(actions.contains("ACTIVATE_POLICY"))
        store.auditLog().forEach { entry ->
            assertTrue(entry.actor.isNotBlank())
            assertTrue(entry.targetId.isNotBlank())
            assertEquals("SUCCESS", entry.outcome)
        }
    }
}

@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN"
    ]
)
@AutoConfigureMockMvc
@Import(RestLoadProbeTest.LoadProvider::class)
class RestLoadProbeTest {
    @Autowired
    private lateinit var http: MockMvc

    @Autowired
    private lateinit var classifier: OneSecondClassifier

    @TestConfiguration
    class LoadProvider {
        @Bean
        @Primary
        fun classifier() = OneSecondClassifier()
    }

    @Test
    fun `NFR-1 local REST precursor stays under two seconds with a one second provider stub`() {
        val pool = Executors.newFixedThreadPool(50)
        val latencies = try {
            pool.invokeAll((1..50).map { index ->
                Callable {
                    val started = System.nanoTime()
                    val response = http.post("/internal/v1/moderations") {
                        header("Authorization", AUTH)
                        header("Idempotency-Key", "load-$index-${System.nanoTime()}")
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"contentId":"load-$index","contentType":"MESSAGE","text":"quoted film line $index"}"""
                    }.andReturn().response
                    assertEquals(200, response.status)
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                }
            }).map { it.get() }.sorted()
        } finally {
            pool.shutdownNow()
        }
        val p95 = latencies[((latencies.size * 0.95).toInt()).coerceAtMost(latencies.lastIndex)]
        println("NFR-1 local in-memory REST 50-request concurrent probe p95=${p95}ms")
        assertTrue(p95 <= 2_000, "p95 was ${p95}ms")
        assertEquals(50, classifier.calls.get())
    }

    class OneSecondClassifier : com.tinder.clone.moderation.application.ports.ModerationClassifierPort {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        private val delegate = com.tinder.clone.moderation.infrastructure.provider.FallbackClassifier()
        override fun classify(content: com.tinder.clone.moderation.domain.model.ModerationContent):
            com.tinder.clone.moderation.application.commands.output.ClassificationResult {
            calls.incrementAndGet()
            Thread.sleep(1_000)
            return delegate.classify(content)
        }
    }
}
