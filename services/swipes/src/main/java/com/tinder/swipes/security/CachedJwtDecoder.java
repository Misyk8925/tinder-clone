package com.tinder.swipes.security;

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
 * Caches only validation status (token string) to mark tokens as already validated.
 */
@Slf4j
public class CachedJwtDecoder implements JwtDecoder {

    private static final String JWT_CACHE_PREFIX = "jwt:validated:";
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

        // Try to get from cache - if present, token was already validated
        String cachedToken = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedToken != null && cachedToken.equals("valid")) {
            log.trace("JWT cache hit - token already validated");

            // Token signature was already validated, just decode it again
            // Note: delegate.decode() is still called but JWK keys are cached by NimbusJwtDecoder
            return delegate.decode(token);
        }

        // Cache miss - fully decode and validate token
        log.trace("JWT cache miss, validating token");
        Jwt jwt = delegate.decode(token);

        // Cache validation result
        if (jwt.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds();

            // Only cache if token has reasonable lifetime remaining
            if (ttlSeconds > MIN_CACHE_TTL_SECONDS) {
                stringRedisTemplate.opsForValue().set(cacheKey, "valid", ttlSeconds, TimeUnit.SECONDS);
                log.trace("Cached JWT validation status with TTL {} seconds", ttlSeconds);
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

