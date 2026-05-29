package com.tinder.profiles.security;

import com.tinder.profiles.profile.cache.ProfileCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeckHotPathTokenCacheTest {

    @Test
    void getProfileId_ReturnsCachedProfileBeforeJwtExpiry() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        DeckHotPathTokenCache cache = cache(now);
        UUID profileId = UUID.randomUUID();

        cache.put(jwt("token-1", now.plusSeconds(60)), profileId);

        assertThat(cache.getProfileId("token-1")).contains(profileId);
    }

    @Test
    void getProfileId_DropsExpiredJwt() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        DeckHotPathTokenCache cache = cache(now);

        cache.put(jwt("token-1", now.minusSeconds(1)), UUID.randomUUID());

        assertThat(cache.getProfileId("token-1")).isEmpty();
    }

    @Test
    void evictProfile_RemovesAllTokensForProfile() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        DeckHotPathTokenCache cache = cache(now);
        UUID profileId = UUID.randomUUID();

        cache.put(jwt("token-1", now.plusSeconds(60)), profileId);
        cache.put(jwt("token-2", now.plusSeconds(60)), profileId);

        cache.evictProfile(profileId);

        assertThat(cache.getProfileId("token-1")).isEmpty();
        assertThat(cache.getProfileId("token-2")).isEmpty();
    }

    @Test
    void put_DoesNotCacheOversizedToken() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        DeckHotPathTokenCache cache = cache(now);
        String token = "a".repeat(9000);

        cache.put(jwt(token, now.plusSeconds(60)), UUID.randomUUID());

        assertThat(cache.getProfileId(token)).isEmpty();
    }

    private DeckHotPathTokenCache cache(Instant now) {
        ProfileCacheProperties properties = new ProfileCacheProperties();
        properties.getDeckHotPath().setTtl(Duration.ofMinutes(5));
        properties.getDeckHotPath().setMaxSize(100);
        properties.getDeckHotPath().setMaxTokenLength(8192);
        return new DeckHotPathTokenCache(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Jwt jwt(String token, Instant expiresAt) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
    }
}
