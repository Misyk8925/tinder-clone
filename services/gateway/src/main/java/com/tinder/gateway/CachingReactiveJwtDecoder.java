package com.tinder.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public class CachingReactiveJwtDecoder implements ReactiveJwtDecoder {

    private final ReactiveJwtDecoder delegate;
    private final Cache<String, Jwt> jwtCache;
    private final Clock clock;
    private final int maxTokenLength;

    public CachingReactiveJwtDecoder(
            ReactiveJwtDecoder delegate,
            Duration ttl,
            long maxSize,
            int maxTokenLength
    ) {
        this(delegate, ttl, maxSize, maxTokenLength, Clock.systemUTC());
    }

    CachingReactiveJwtDecoder(
            ReactiveJwtDecoder delegate,
            Duration ttl,
            long maxSize,
            int maxTokenLength,
            Clock clock
    ) {
        this.delegate = delegate;
        this.clock = clock;
        this.maxTokenLength = maxTokenLength;
        this.jwtCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        if (token == null || token.length() > maxTokenLength) {
            return Mono.error(new JwtException("JWT token is missing or too large"));
        }

        String cacheKey = cacheKey(token);
        Jwt cached = jwtCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (isUsable(cached)) {
                return Mono.just(cached);
            }
            jwtCache.invalidate(cacheKey);
        }

        return delegate.decode(token)
                .doOnNext(jwt -> {
                    if (isUsable(jwt)) {
                        jwtCache.put(cacheKey, jwt);
                    }
                });
    }

    private boolean isUsable(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(clock.instant());
    }

    private String cacheKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new JwtException("Failed to build JWT cache key", e);
        }
    }
}
