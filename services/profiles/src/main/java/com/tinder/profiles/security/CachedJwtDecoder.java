package com.tinder.profiles.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * JWT Decoder with Redis caching to avoid validating the same token multiple times.
 * Caches decoded JWT objects by token string as key.
 */
@Slf4j
public class CachedJwtDecoder implements JwtDecoder {

    private static final String JWT_CACHE_PREFIX = "jwt:cache:";
    private static final long MIN_CACHE_TTL_SECONDS = 60; // minimum 1 minute cache

    private final JwtDecoder delegate;
    private final RedisTemplate<String, Object> redisTemplate;

    public CachedJwtDecoder(JwtDecoder delegate, RedisTemplate<String, Object> redisTemplate) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String cacheKey = JWT_CACHE_PREFIX + hashToken(token);

        // Try to get from cache
        Jwt cachedJwt = (Jwt) redisTemplate.opsForValue().get(cacheKey);
        if (cachedJwt != null) {
            log.trace("JWT cache hit for token");

            // Verify token hasn't expired
            if (cachedJwt.getExpiresAt() != null && cachedJwt.getExpiresAt().isAfter(Instant.now())) {
                return cachedJwt;
            } else {
                log.debug("Cached JWT expired, removing from cache");
                redisTemplate.delete(cacheKey);
            }
        }

        // Cache miss - decode and validate token
        log.trace("JWT cache miss, validating token");
        Jwt jwt = delegate.decode(token);

        // Cache the decoded JWT
        if (jwt.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds();

            // Only cache if token has reasonable lifetime remaining
            if (ttlSeconds > MIN_CACHE_TTL_SECONDS) {
                redisTemplate.opsForValue().set(cacheKey, jwt, ttlSeconds, TimeUnit.SECONDS);
                log.trace("Cached JWT with TTL {} seconds", ttlSeconds);
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

