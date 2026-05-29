package com.tinder.profiles.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tinder.profiles.profile.cache.ProfileCacheProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

public class CachingJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final Cache<String, Jwt> jwtCache;
    private final Clock clock;

    public CachingJwtDecoder(JwtDecoder delegate, ProfileCacheProperties properties) {
        this(delegate, properties, Clock.systemUTC());
    }

    CachingJwtDecoder(JwtDecoder delegate, ProfileCacheProperties properties, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
        this.jwtCache = Caffeine.newBuilder()
                .maximumSize(properties.getJwtToken().getMaxSize())
                .expireAfterWrite(properties.getJwtToken().getTtl())
                .build();
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String cacheKey = cacheKey(token);
        Jwt cached = jwtCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (isUsable(cached)) {
                return cached;
            }
            jwtCache.invalidate(cacheKey);
        }

        Jwt decoded = delegate.decode(token);
        if (isUsable(decoded)) {
            jwtCache.put(cacheKey, decoded);
        }
        return decoded;
    }

    private boolean isUsable(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now(clock));
    }

    private String cacheKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new JwtException("Failed to build JWT cache key", e);
        }
    }
}
