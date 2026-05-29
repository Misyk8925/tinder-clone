package com.tinder.profiles.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tinder.profiles.profile.cache.ProfileCacheProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeckHotPathTokenCache {

    private final Cache<String, CachedToken> tokens;
    private final ConcurrentHashMap<UUID, Set<String>> tokensByProfileId = new ConcurrentHashMap<>();
    private final ProfileCacheProperties properties;
    private final Clock clock;

    @Autowired
    public DeckHotPathTokenCache(ProfileCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DeckHotPathTokenCache(ProfileCacheProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.tokens = Caffeine.newBuilder()
                .maximumSize(properties.getDeckHotPath().getMaxSize())
                .expireAfterWrite(properties.getDeckHotPath().getTtl())
                .removalListener((String key, CachedToken value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (key != null && value != null) {
                        removeTokenFromProfileIndex(value.profileId(), key);
                    }
                })
                .build();
    }

    public void put(Jwt jwt, UUID profileId) {
        if (!properties.getDeckHotPath().isEnabled() || jwt == null || profileId == null) {
            return;
        }

        String tokenValue = jwt.getTokenValue();
        if (!isTokenShapeAllowed(tokenValue)) {
            return;
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now(clock))) {
            return;
        }

        String key = cacheKey(tokenValue);
        CachedToken previous = tokens.getIfPresent(key);
        if (previous != null && !previous.profileId().equals(profileId)) {
            removeTokenFromProfileIndex(previous.profileId(), key);
        }
        tokensByProfileId.computeIfAbsent(profileId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
        tokens.put(key, new CachedToken(profileId, expiresAt));
    }

    public Optional<UUID> getProfileId(String tokenValue) {
        if (!properties.getDeckHotPath().isEnabled() || !isTokenShapeAllowed(tokenValue)) {
            return Optional.empty();
        }

        String key = cacheKey(tokenValue);
        CachedToken cached = tokens.getIfPresent(key);
        if (cached == null) {
            return Optional.empty();
        }

        Instant expiresAt = cached.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now(clock))) {
            tokens.invalidate(key);
            removeTokenFromProfileIndex(cached.profileId(), key);
            return Optional.empty();
        }

        return Optional.of(cached.profileId());
    }

    public void evictProfile(UUID profileId) {
        if (profileId == null) {
            return;
        }

        Set<String> keys = tokensByProfileId.remove(profileId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.forEach(tokens::invalidate);
    }

    private boolean isTokenShapeAllowed(String tokenValue) {
        return tokenValue != null
                && !tokenValue.isBlank()
                && tokenValue.length() <= properties.getDeckHotPath().getMaxTokenLength();
    }

    private void removeTokenFromProfileIndex(UUID profileId, String key) {
        Set<String> keys = tokensByProfileId.get(profileId);
        if (keys == null) {
            return;
        }

        keys.remove(key);
        if (keys.isEmpty()) {
            tokensByProfileId.remove(profileId, keys);
        }
    }

    private String cacheKey(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build deck hot path token cache key", e);
        }
    }

    private record CachedToken(UUID profileId, Instant expiresAt) {}
}
