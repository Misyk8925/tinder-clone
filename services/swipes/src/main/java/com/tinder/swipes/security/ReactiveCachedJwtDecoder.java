package com.tinder.swipes.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Reactive JWT Decoder with Redis caching to avoid validating the same token multiple times.
 * Caches only validation status (token string) to mark tokens as already validated.
 * Uses blocking Redis operations wrapped in Mono.fromCallable() for simplicity.
 */
@Slf4j
public class ReactiveCachedJwtDecoder implements ReactiveJwtDecoder {

    private static final String JWT_CACHE_PREFIX = "jwt:validated:";
    private static final long MIN_CACHE_TTL_SECONDS = 60; // minimum 1 minute cache

    private final ReactiveJwtDecoder delegate;
    private final StringRedisTemplate stringRedisTemplate;

    public ReactiveCachedJwtDecoder(ReactiveJwtDecoder delegate, StringRedisTemplate stringRedisTemplate) {
        this.delegate = delegate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        String cacheKey = JWT_CACHE_PREFIX + hashToken(token);
        Mono<Jwt> decodeAndCache = Mono.defer(() -> delegate.decode(token))
                .doOnSuccess(jwt -> cacheValidationResult(cacheKey, jwt));

        return Mono.fromCallable(() -> stringRedisTemplate.opsForValue().get(cacheKey))
                .onErrorResume(e -> {
                    // On cache read errors, fall back to direct validation.
                    log.trace("Cache check failed, falling back to direct validation", e);
                    return Mono.empty();
                })
                .flatMap(cachedToken -> {
                    if ("valid".equals(cachedToken)) {
                        log.trace("JWT cache hit - token already validated");
                        // Token signature was already validated, decode token claims again.
                        return delegate.decode(token);
                    }
                    log.trace("JWT cache miss, validating token");
                    return decodeAndCache;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.trace("JWT cache miss, validating token");
                    return decodeAndCache;
                }));
    }

    private void cacheValidationResult(String cacheKey, Jwt jwt) {
        if (jwt.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds();

            // Only cache if token has reasonable lifetime remaining
            if (ttlSeconds > MIN_CACHE_TTL_SECONDS) {
                try {
                    stringRedisTemplate.opsForValue().set(cacheKey, "valid", ttlSeconds, TimeUnit.SECONDS);
                    log.trace("Cached JWT validation status with TTL {} seconds", ttlSeconds);
                } catch (Exception e) {
                    log.warn("Failed to cache JWT validation result", e);
                }
            }
        }
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
