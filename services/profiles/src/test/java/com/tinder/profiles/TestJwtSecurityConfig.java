package com.tinder.profiles;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Base64;

/** Decodes fixture bearer tokens locally; no Keycloak is needed for service-level HTTP tests. */
@TestConfiguration
public class TestJwtSecurityConfig {

    @Bean
    @Primary
    JwtDecoder fixtureJwtDecoder() {
        return token -> {
            String subject = new String(Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8);
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        };
    }

    public static String bearer(String subject) {
        return "Bearer " + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
