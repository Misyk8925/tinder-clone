package com.tinder.match.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the Keycloak user ID (JWT {@code sub} claim) of the caller to the profile UUID
 * that conversations and matches are keyed by.
 * <p>
 * The mapping is <strong>never</strong> taken from the request: a caller-supplied profile ID
 * would let anyone claim another user's identity and then read or write that user's
 * conversations. Instead the profiles service is asked, with the caller's own bearer token,
 * which profile that token owns ({@code GET /api/v1/profiles/me}). The answer is therefore
 * only ever the caller's own profile.
 * <p>
 * Successful lookups are cached for a short while so the chat hot path does not make one
 * upstream call per message.
 */
@Service
@Slf4j
public class UserProfileMappingService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_ENTRIES = 50_000;

    private final RestClient profilesRestClient;
    private final ConcurrentHashMap<String, CachedProfileId> cache = new ConcurrentHashMap<>();

    public UserProfileMappingService(RestClient profilesRestClient) {
        this.profilesRestClient = profilesRestClient;
    }

    /**
     * Resolve the profile UUID owned by the authenticated caller.
     *
     * @param userId      JWT {@code sub} of the caller
     * @param bearerToken the caller's raw access token, used to authenticate the upstream call
     * @return the caller's profile UUID, or {@code null} when it cannot be established
     */
    public UUID resolve(String userId, String bearerToken) {
        if (userId == null || userId.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            return null;
        }

        CachedProfileId cached = cache.get(userId);
        if (cached != null && cached.isFresh()) {
            return cached.profileId();
        }

        UUID profileId = fetchProfileId(bearerToken);
        if (profileId != null) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(userId, new CachedProfileId(profileId, Instant.now().plus(CACHE_TTL)));
        }
        return profileId;
    }

    /** Drops any cached mapping, e.g. after a profile was recreated. */
    public void invalidate(String userId) {
        if (userId != null) {
            cache.remove(userId);
        }
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
