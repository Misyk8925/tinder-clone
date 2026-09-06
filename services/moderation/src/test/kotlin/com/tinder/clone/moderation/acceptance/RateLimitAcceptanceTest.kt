package com.tinder.clone.moderation.acceptance

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@Tag("acceptance")
@SpringBootTest(
    properties = [
        "moderation.security.users[0].username=policy-admin",
        "moderation.security.users[0].password-hash=\$2b\$10\$M54tN0U6On./PPN3kwO36OSimTyRteHIJwtVwEI9oakcoFw1V3Glu",
        "moderation.security.users[0].roles=VIEWER,MODERATOR,POLICY_ADMIN",
        "moderation.traffic.requests-per-minute=1",
    ]
)
@AutoConfigureMockMvc
class RateLimitAcceptanceTest {
    @Autowired
    private lateinit var http: MockMvc

    private val auth = "Basic cG9saWN5LWFkbWluOnRlc3Qtb25seQ=="

    @Test
    fun `ERR-HTTP-429 rate limit includes retry information`() {
        repeat(2) { index ->
            val result = http.post("/internal/v1/moderations") {
                header("Authorization", auth)
                header("Idempotency-Key", "rate-limit-$index")
                contentType = MediaType.APPLICATION_JSON
                content = """{"contentId":"rate-$index","contentType":"MESSAGE","text":"quoted film line"}"""
            }.andReturn().response
            if (index == 1) {
                kotlin.test.assertEquals(429, result.status)
                kotlin.test.assertNotNull(result.getHeader("Retry-After"))
                kotlin.test.assertTrue(result.contentAsString.contains("RATE_LIMITED"))
            }
        }
    }
}
