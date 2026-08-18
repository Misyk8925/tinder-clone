package com.tinder.profiles.config.security;
import com.tinder.profiles.config.security.CachingJwtDecoder;

import com.tinder.profiles.config.props.ProfileCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingJwtDecoderTest {

    @Test
    void decode_ReusesCachedJwtBeforeExpiry() {
        AtomicInteger calls = new AtomicInteger();
        Jwt jwt = jwt("token-1", Instant.now().plusSeconds(60));
        CachingJwtDecoder decoder = decoder(token -> {
            calls.incrementAndGet();
            return jwt;
        }, Instant.now());

        Jwt first = decoder.decode("token-1");
        Jwt second = decoder.decode("token-1");

        assertThat(first).isSameAs(jwt);
        assertThat(second).isSameAs(jwt);
        assertThat(calls).hasValue(1);
    }

    @Test
    void decode_DoesNotReuseExpiredJwt() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        CachingJwtDecoder decoder = decoder(token -> {
            calls.incrementAndGet();
            return jwt(token, now.minusSeconds(1));
        }, now);

        decoder.decode("token-1");
        decoder.decode("token-1");

        assertThat(calls).hasValue(2);
    }

    private CachingJwtDecoder decoder(JwtDecoder delegate, Instant now) {
        ProfileCacheProperties.CacheSpec tokenCache =
                new ProfileCacheProperties.CacheSpec(Duration.ofMinutes(5), 100);
        ProfileCacheProperties properties =
                new ProfileCacheProperties(tokenCache, tokenCache, tokenCache);
        return new CachingJwtDecoder(delegate, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Jwt jwt(String token, Instant expiresAt) {
        Instant issuedAt = expiresAt.minusSeconds(60);
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
