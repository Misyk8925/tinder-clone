package com.tinder.deckread.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;

/**
 * In-process cache of Keycloak userId (JWT {@code sub}) → profileId.
 *
 * <p>Decks in Redis are keyed by profileId while the client authenticates with the JWT sub,
 * so every deck read needs this mapping. It is immutable for the lifetime of a profile
 * (only delete/recreate changes it), so a TTL'd local cache collapses the per-request
 * profiles lookup into a map hit.
 */
@ApplicationScoped
public class ViewerIdentityCache {

    @ConfigProperty(name = "deck-read.viewer-id-cache.ttl-seconds", defaultValue = "1800")
    long ttlSeconds;

    @ConfigProperty(name = "deck-read.viewer-id-cache.max-size", defaultValue = "200000")
    long maxSize;

    @Inject
    MeterRegistry meterRegistry;

    private Cache<String, UUID> cache;

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "deckread.viewer-id");
    }

    public UUID get(String userId) {
        return userId == null ? null : cache.getIfPresent(userId);
    }

    public void put(String userId, UUID profileId) {
        if (userId != null && profileId != null) {
            cache.put(userId, profileId);
        }
    }
}
