package com.tinder.profiles.security;

import com.tinder.profiles.profile.cache.DeckPageCacheService;
import com.tinder.profiles.profile.cache.ProfileCacheProperties;
import com.tinder.profiles.profile.cache.ProfileIdentityCacheService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeckHotPathFilterTest {

    @Test
    void doFilter_ServesCachedJsonForKnownTokenAndCachedPage() throws Exception {
        UUID profileId = UUID.randomUUID();
        byte[] json = "[{\"id\":\"p1\"}]".getBytes(StandardCharsets.UTF_8);
        DeckPageCacheService pageCache = mock(DeckPageCacheService.class);
        when(pageCache.getBytes(eq(profileId), eq(0), eq(20))).thenReturn(json);

        DeckHotPathTokenCache tokenCache = tokenCache();
        tokenCache.put(jwt("token-1"), profileId);

        DeckHotPathFilter filter = filter(tokenCache, pageCache, identityCache());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profiles/deck");
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(json);
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    void doFilter_FallsThroughWhenTokenIsUnknown() throws Exception {
        DeckHotPathFilter filter = filter(tokenCache(), mock(DeckPageCacheService.class), identityCache());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profiles/deck");
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void doFilter_RejectsOversizedBearerTokenBeforeSecurityChain() throws Exception {
        DeckHotPathFilter filter = filter(tokenCache(), mock(DeckPageCacheService.class), identityCache());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profiles/deck");
        request.addHeader("Authorization", "Bearer " + "a".repeat(9000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_ServesCachedJsonForGatewayInternalAuthAndCachedProfileId() throws Exception {
        UUID profileId = UUID.randomUUID();
        byte[] json = "[{\"id\":\"p1\"}]".getBytes(StandardCharsets.UTF_8);
        DeckPageCacheService pageCache = mock(DeckPageCacheService.class);
        when(pageCache.getBytes(eq(profileId), eq(0), eq(20))).thenReturn(json);

        ProfileIdentityCacheService identityCache = identityCache();
        when(identityCache.getCachedProfileId("user-1")).thenReturn(profileId);

        DeckHotPathFilter filter = filter(tokenCache(), pageCache, identityCache);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profiles/deck");
        request.addHeader(InternalAuthVerifier.HEADER_NAME, "secret");
        request.addHeader(InternalAuthVerifier.USER_SUBJECT_HEADER, "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(json);
    }

    private DeckHotPathTokenCache tokenCache() {
        ProfileCacheProperties properties = properties();
        return new DeckHotPathTokenCache(
                properties,
                Clock.fixed(Instant.parse("2026-04-28T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private ProfileCacheProperties properties() {
        ProfileCacheProperties properties = new ProfileCacheProperties();
        properties.getDeckHotPath().setTtl(Duration.ofMinutes(5));
        properties.getDeckHotPath().setMaxSize(100);
        properties.getDeckHotPath().setMaxTokenLength(8192);
        return properties;
    }

    private DeckHotPathFilter filter(
            DeckHotPathTokenCache tokenCache,
            DeckPageCacheService pageCache,
            ProfileIdentityCacheService identityCache
    ) {
        return new DeckHotPathFilter(
                tokenCache,
                pageCache,
                properties(),
                new InternalAuthVerifier("secret"),
                identityCache
        );
    }

    private ProfileIdentityCacheService identityCache() {
        return mock(ProfileIdentityCacheService.class);
    }

    private Jwt jwt(String token) {
        Instant expiresAt = Instant.parse("2026-04-28T00:05:00Z");
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
    }

    private FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
