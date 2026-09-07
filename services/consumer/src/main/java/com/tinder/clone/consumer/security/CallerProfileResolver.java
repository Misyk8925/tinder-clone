package com.tinder.clone.consumer.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the profile owned by the authenticated caller.
 * <p>
 * The gateway injects a verified {@code X-User-Id}, but that only holds for requests that
 * actually came through the gateway. Anything able to reach this service directly could present
 * a valid token together with someone else's profile id and read their likes. So the identity is
 * established here instead: the profiles service is asked, with the caller's own bearer token,
 * which profile that token owns. The answer can only ever be the caller's.
 * <p>
 * Lookups are cached briefly per Keycloak user id — the mapping cannot change for the life of a
 * token, and this sits on a user-facing read.
 */
@Component
@Slf4j
public class CallerProfileResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private static final int MAX_CACHE_ENTRIES = 50_000;

    private final RestClient profilesRestClient;
    private final ConcurrentHashMap<String, CachedProfileId> cache = new ConcurrentHashMap<>();

    public CallerProfileResolver(RestClient profilesRestClient) {
        this.profilesRestClient = profilesRestClient;
    }

    /** @return the caller's own profile id, or {@code null} when it cannot be established. */
    public UUID resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getTokenValue() == null) {
            return null;
        }

        CachedProfileId cached = cache.get(jwt.getSubject());
        if (cached != null && cached.isFresh()) {
            return cached.profileId();
        }

        UUID profileId = fetchProfileId(jwt.getTokenValue());
        if (profileId != null) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(jwt.getSubject(), new CachedProfileId(profileId, Instant.now().plus(CACHE_TTL)));
        }
        return profileId;
    }

    @SuppressWarnings("unchecked")
    private UUID fetchProfileId(String bearerToken) {
        try {
            Map<String, Object> body = profilesRestClient.get()
                    .uri("/api/v1/profiles/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(Map.class);
            Object rawProfileId = body == null ? null : body.get("profileId");
            return rawProfileId == null ? null : UUID.fromString(rawProfileId.toString());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Unable to resolve caller profile id from profiles service: {}", e.getMessage());
            return null;
        }
    }

    private record CachedProfileId(UUID profileId, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
