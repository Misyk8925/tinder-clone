package com.tinder.clone.moderation.acceptance

import com.tinder.clone.moderation.application.ports.MutationAuditPort
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertTrue

@Tag("acceptance")
@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN",
        "moderation.security.users[1].username=viewer",
        "moderation.security.users[1].password-hash=\$2b\$10\$B8JziNtYQcAfaF4OUFXpxejHeLQ.jmCsma5ZRP65d6f.Eli5ADkJ.",
        "moderation.security.users[1].roles=VIEWER"
    ]
)
@AutoConfigureMockMvc
@Import(AuditFailureAcceptanceTest.AuditFixture::class)
class AuditFailureAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc

    @Autowired
    private lateinit var audit: RecordingAudit

    @TestConfiguration
    class AuditFixture {
        @Bean
        @Primary
        fun mutationAudit() = RecordingAudit()
    }

    @Test
    fun `rejected policy mutations record actor target and failure outcome`() {
        http.post("/internal/v1/policies") {
            header("Authorization", "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ==")
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"audit-invalid-v1","scopes":[]}"""
        }
        http.post("/internal/v1/policies/audit-invalid-v1/publication") {
            header("Authorization", "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ==")
            header("If-Match", "\"0\"")
        }.andExpect { status { isUnprocessableContent() } }

        http.post("/internal/v1/policies") {
            header("Authorization", "Basic dmlld2VyOnZpZXdlci1vbmx5")
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":"audit-forbidden-v1","scopes":[]}"""
        }.andExpect { status { isForbidden() } }

        assertTrue(audit.entries.any {
            it.actor == "policy-admin" &&
                it.action == "PUBLISH_POLICY" &&
                it.targetId == "audit-invalid-v1" &&
                it.outcome == "POLICY_INVALID"
        })
        assertTrue(audit.entries.any {
            it.actor == "viewer" &&
                it.targetId == "/internal/v1/policies" &&
                it.outcome == "FORBIDDEN"
        })
    }

    data class Entry(
        val actor: String,
        val action: String,
        val targetType: String,
        val targetId: String,
        val outcome: String
    )

    class RecordingAudit : MutationAuditPort {
        val entries = CopyOnWriteArrayList<Entry>()
        override fun recordFailure(
            actor: String,
            action: String,
            targetType: String,
            targetId: String,
            outcome: String
        ) {
            entries += Entry(actor, action, targetType, targetId, outcome)
        }
    }
}
