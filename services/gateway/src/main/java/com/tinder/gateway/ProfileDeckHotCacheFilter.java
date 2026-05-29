package com.tinder.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class ProfileDeckHotCacheFilter extends AbstractGatewayFilterFactory<Object> {

    private static final String PROFILE_ID_KEY_PREFIX = "profiles:jwt-sub:";
    private static final String DECK_PAGE_KEY_PREFIX = "profiles:deck-page:";
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final StatefulRedisConnection<String, String> redisConnection;
    private final RedisCommands<String, String> redis;
    private final boolean enabled;
    private final Cache<String, String> profileIds;
    private final Cache<String, byte[]> deckPages;

    public ProfileDeckHotCacheFilter(
            RedisClient redisClient,
            @Value("${gateway.profile-deck-hot-cache.enabled:true}") boolean enabled,
            @Value("${gateway.profile-deck-hot-cache.ttl:30m}") Duration ttl,
            @Value("${gateway.profile-deck-hot-cache.profile-id-max-size:10000}") long profileIdMaxSize,
            @Value("${gateway.profile-deck-hot-cache.page-max-size:10000}") long pageMaxSize
    ) {
        super(Object.class);
        this.redisConnection = redisClient.connect();
        this.redis = redisConnection.sync();
        this.enabled = enabled;
        this.profileIds = Caffeine.newBuilder()
                .maximumSize(profileIdMaxSize)
                .expireAfterWrite(ttl)
                .build();
        this.deckPages = Caffeine.newBuilder()
                .maximumSize(pageMaxSize)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            if (!enabled) {
                return chain.filter(exchange);
            }

            PageRequest pageRequest = pageRequest(exchange);
            if (pageRequest == null) {
                return chain.filter(exchange);
            }

            return resolveVerifiedSubject(exchange)
                    .flatMap(subject -> resolveProfileId(subject)
                            .flatMap(profileId -> resolveDeckPage(profileId, pageRequest)
                                    .flatMap(json -> writeJson(exchange, json).thenReturn(Boolean.TRUE))))
                    .defaultIfEmpty(Boolean.FALSE)
                    .flatMap(written -> written ? Mono.empty() : chain.filter(exchange));
        }, -90);
    }

    private Mono<String> resolveVerifiedSubject(ServerWebExchange exchange) {
        Object verified = exchange.getAttribute(ProfileDeckJwtAuthFilter.VERIFIED_SUBJECT_ATTRIBUTE);
        if (verified instanceof String subject && !subject.isBlank()) {
            return Mono.just(subject);
        }

        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(jwt -> jwt.getToken().getSubject())
                .filter(subject -> subject != null && !subject.isBlank());
    }

    private Mono<String> resolveProfileId(String subject) {
        String cached = profileIds.getIfPresent(subject);
        if (cached != null) {
            return Mono.just(cached);
        }

        return Mono.fromCallable(() -> redis.get(PROFILE_ID_KEY_PREFIX + subject))
                .subscribeOn(Schedulers.boundedElastic())
                .filter(profileId -> !profileId.isBlank())
                .doOnNext(profileId -> profileIds.put(subject, profileId))
                .onErrorResume(ignored -> Mono.empty());
    }

    private Mono<byte[]> resolveDeckPage(String profileId, PageRequest pageRequest) {
        String key = DECK_PAGE_KEY_PREFIX + profileId + ":" + pageRequest.offset() + ":" + pageRequest.limit();
        byte[] cached = deckPages.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }

        return Mono.fromCallable(() -> redis.get(key))
                .subscribeOn(Schedulers.boundedElastic())
                .map(json -> json.getBytes(StandardCharsets.UTF_8))
                .doOnNext(bytes -> deckPages.put(key, bytes))
                .onErrorResume(ignored -> Mono.empty());
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, byte[] json) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.fromSupplier(() -> response.bufferFactory().wrap(json)));
    }

    private PageRequest pageRequest(ServerWebExchange exchange) {
        int offset = intQuery(exchange, "offset", DEFAULT_OFFSET);
        int limit = intQuery(exchange, "limit", DEFAULT_LIMIT);
        if (offset < 0 || limit < 1 || limit > MAX_LIMIT) {
            return null;
        }
        return new PageRequest(offset, limit);
    }

    private int intQuery(ServerWebExchange exchange, String name, int defaultValue) {
        String value = exchange.getRequest().getQueryParams().getFirst(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    @PreDestroy
    public void close() {
        redisConnection.close();
    }

    private record PageRequest(int offset, int limit) {}
}
