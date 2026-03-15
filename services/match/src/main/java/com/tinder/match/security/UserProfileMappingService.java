package com.tinder.match.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds an in-memory mapping of Keycloak user ID (JWT sub claim) → profile UUID.
 * <p>
 * Conversations store participant IDs as profile UUIDs, but the STOMP principal name
 * is the Keycloak user ID (JWT sub). This service bridges the two so the WebSocket
 * controller can resolve the correct profile ID when validating conversation access.
 * <p>
 * Entries are registered via the REST layer (create-conversation / get-conversation)
 * where the caller is authenticated and self-declares their profile ID, which is then
 * validated against the conversation participants before being stored.
 */
@Service
@Slf4j
public class UserProfileMappingService {

    private final ConcurrentHashMap<String, UUID> userIdToProfileId = new ConcurrentHashMap<>();

    /**
     * Register a Keycloak user ID → profile ID mapping.
     *
     * @param userId    JWT sub claim (Keycloak user UUID as String)
     * @param profileId the caller's profile UUID
     */
    public void register(String userId, UUID profileId) {
        if (userId == null || profileId == null) return;
        userIdToProfileId.put(userId, profileId);
        log.debug("Registered user mapping userId={} profileId={}", userId, profileId);
    }

    /**
     * Look up the profile UUID for a Keycloak user ID.
     *
     * @param userId JWT sub claim
     * @return profile UUID, or {@code null} if not yet registered
     */
    public UUID resolve(String userId) {
        return userId == null ? null : userIdToProfileId.get(userId);
    }
}
