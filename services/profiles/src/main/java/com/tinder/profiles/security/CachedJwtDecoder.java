package com.tinder.profiles.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * JWT Decoder with Redis caching to avoid validating the same token multiple times.
 * Caches only validation status to avoid serializing Spring Security Jwt objects.
 */
@Slf4j
public class CachedJwtDecoder implements JwtDecoder {

    private static final String JWT_CACHE_PREFIX = "jwt:cache:";
    private static final String VALID_CACHE_VALUE = "valid";
    private static final long MIN_CACHE_TTL_SECONDS = 60; // minimum 1 minute cache

    private final JwtDecoder delegate;
    private final StringRedisTemplate stringRedisTemplate;

    public CachedJwtDecoder(JwtDecoder delegate, StringRedisTemplate stringRedisTemplate) {
        this.delegate = delegate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String cacheKey = JWT_CACHE_PREFIX + hashToken(token);

        // Try to get validation marker from cache.
        // If key exists, we still decode to build Authentication from current token claims.
        String cachedStatus = stringRedisTemplate.opsForValue().get(cacheKey);
        if (VALID_CACHE_VALUE.equals(cachedStatus)) {
            log.trace("JWT cache hit - token previously validated");
            return delegate.decode(token);
        }
        if (cachedStatus != null) {
            // Defensive cleanup for unexpected old cache format.
            log.debug("Unexpected JWT cache value format, evicting key");
            stringRedisTemplate.delete(cacheKey);
        }

        // Cache miss - decode and validate token
        log.trace("JWT cache miss, validating token");
        Jwt jwt = delegate.decode(token);

        // Cache the decoded JWT
        if (jwt.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds();

            // Only cache if token has reasonable lifetime remaining
            if (ttlSeconds > MIN_CACHE_TTL_SECONDS) {
                stringRedisTemplate.opsForValue().set(cacheKey, VALID_CACHE_VALUE, ttlSeconds, TimeUnit.SECONDS);
                log.trace("Cached JWT validation marker with TTL {} seconds", ttlSeconds);
            }
        }

        return jwt;
    }

    /**
     * Generate a hash of the token for use as cache key.
     * Uses substring to avoid storing full token in Redis keys.
     */
    private String hashToken(String token) {
        // Use token hashCode + last chars for cache key
        // This avoids storing full token in Redis key names
        int hash = token.hashCode();
        String suffix = token.length() > 20 ? token.substring(token.length() - 20) : token;
        return hash + ":" + suffix.hashCode();
    }
}
