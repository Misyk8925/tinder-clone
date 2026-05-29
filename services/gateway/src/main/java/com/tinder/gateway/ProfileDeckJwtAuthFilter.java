package com.tinder.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
public class ProfileDeckJwtAuthFilter extends AbstractGatewayFilterFactory<Object> {

    public static final String VERIFIED_SUBJECT_ATTRIBUTE = "gateway.profile-deck.verified-subject";

    private final ReactiveJwtDecoder jwtDecoder;
    private final int maxTokenLength;
    private final Cache<String, CachedSubject> subjects;

    public ProfileDeckJwtAuthFilter(
            ReactiveJwtDecoder jwtDecoder,
            @Value("${gateway.jwt-cache.max-token-length:8192}") int maxTokenLength,
            @Value("${gateway.profile-deck-jwt-subject-cache.ttl:5m}") Duration ttl,
            @Value("${gateway.profile-deck-jwt-subject-cache.max-size:10000}") long maxSize
    ) {
        super(Object.class);
        this.jwtDecoder = jwtDecoder;
        this.maxTokenLength = maxTokenLength;
        this.subjects = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            String token = bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (token == null || token.length() > maxTokenLength) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            CachedSubject cached = subjects.getIfPresent(token);
            if (cached != null) {
                if (cached.isUsable()) {
                    exchange.getAttributes().put(VERIFIED_SUBJECT_ATTRIBUTE, cached.subject());
                    return chain.filter(exchange);
                }
                subjects.invalidate(token);
            }

            return jwtDecoder.decode(token)
                    .flatMap(jwt -> {
                        String subject = jwt.getSubject();
                        if (subject == null || subject.isBlank()) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        subjects.put(token, new CachedSubject(subject, jwt.getExpiresAt()));
                        exchange.getAttributes().put(VERIFIED_SUBJECT_ATTRIBUTE, subject);
                        return chain.filter(exchange);
                    })
                    .onErrorResume(ignored -> {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
        }, -100);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    private record CachedSubject(String subject, Instant expiresAt) {
        boolean isUsable() {
            return subject != null && !subject.isBlank()
                    && (expiresAt == null || expiresAt.isAfter(Instant.now()));
        }
    }
}
