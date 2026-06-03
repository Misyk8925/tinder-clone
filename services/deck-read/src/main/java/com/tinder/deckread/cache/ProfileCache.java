package com.tinder.deckread.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tinder.deckread.dto.DeckCardDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;

/**
 * In-process cache of hydrated profiles, keyed by profileId, with two freshness tiers.
 *
 * <p>Deck reads hydrate the same (popular) profiles repeatedly, and profiles change far less
 * often than decks are read, so caching them collapses most hydration traffic into local lookups.
 *
 * <p>Two tiers support stale-while-revalidate:
 * <ul>
 *   <li><b>fresh</b> — entry younger than {@code ttl-seconds}; served on the happy path.</li>
 *   <li><b>stale</b> — entry older than the fresh TTL but still within the hard
 *       {@code stale-ttl-seconds} cap; served ONLY as a fallback when the profiles service is
 *       unavailable (circuit open / timeout), so a downstream outage degrades to slightly-stale
 *       data instead of a 5xx.</li>
 * </ul>
 * Caffeine stats are bound to Micrometer for a hit-ratio metric.
 */
@ApplicationScoped
public class ProfileCache {

    @ConfigProperty(name = "deck-read.profile-cache.ttl-seconds", defaultValue = "60")
    long ttlSeconds;

    @ConfigProperty(name = "deck-read.profile-cache.stale-ttl-seconds", defaultValue = "600")
    long staleTtlSeconds;

    @ConfigProperty(name = "deck-read.profile-cache.max-size", defaultValue = "100000")
    long maxSize;

    @Inject
    MeterRegistry meterRegistry;

    private Cache<UUID, Entry> cache;

    private record Entry(DeckCardDto card, long storedAtMillis) {}

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                // Hard cap = the stale window; freshness within it is decided per-lookup.
                .expireAfterWrite(Duration.ofSeconds(staleTtlSeconds))
                .recordStats()
                .build();
        // Exposes deckread.profiles cache.gets{result=hit|miss}, cache.size, etc.
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "deckread.profiles");
    }

    /** Return the card only if it is still within the fresh TTL; otherwise null. */
    public DeckCardDto getFresh(UUID profileId) {
        Entry e = cache.getIfPresent(profileId);
        if (e == null) {
            return null;
        }
        return (System.currentTimeMillis() - e.storedAtMillis()) <= ttlSeconds * 1000 ? e.card() : null;
    }

    /** Return any surviving (possibly stale) card — fallback for when hydration fails. */
    public DeckCardDto getStale(UUID profileId) {
        Entry e = cache.getIfPresent(profileId);
        return e == null ? null : e.card();
    }

    public void put(DeckCardDto card) {
        if (card != null && card.profileId() != null) {
            cache.put(card.profileId(), new Entry(card, System.currentTimeMillis()));
        }
    }
}
