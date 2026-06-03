package com.tinder.deckread.service;

import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deckread.cache.ProfileCache;
import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.client.ProfilesClient;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.redis.DeckRedisReader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates a deck read:
 * <pre>
 *   read(viewerId) ── empty? ──> ensure(viewerId) ──> re-read ──> hydrate ──> reorder
 * </pre>
 * Returns hydrated {@link SharedProfileDto}s in deck order. If the deck is still empty
 * after ensure, returns an empty list (no emergency on-the-fly build — see spec).
 *
 * <p>Hydration serves cache-fresh profiles locally, batch-fetches only the misses, and — when the
 * profiles service is unavailable (circuit open / timeout) — falls back to stale cached copies so
 * the endpoint degrades gracefully instead of failing. Per-stage Micrometer timers expose where
 * time goes.
 */
@ApplicationScoped
public class DeckQueryService {

    @Inject
    DeckRedisReader redisReader;

    @Inject
    @RestClient
    DeckEnsureClient deckEnsureClient;

    @Inject
    @RestClient
    ProfilesClient profilesClient;

    @Inject
    ProfileCache profileCache;

    @Inject
    MeterRegistry registry;

    public Uni<List<DeckCardDto>> getDeck(UUID viewerId, int offset, int limit) {
        Timer.Sample readSample = Timer.start(registry);
        return redisReader.read(viewerId, offset, limit)
                .onItemOrFailure().invoke((r, f) -> readSample.stop(registry.timer("deckread.redis.read")))
                .flatMap(ids -> ids.isEmpty()
                        ? ensureThenReread(viewerId, offset, limit)
                        : Uni.createFrom().item(ids))
                .flatMap(this::hydrate);
    }

    private Uni<List<UUID>> ensureThenReread(UUID viewerId, int offset, int limit) {
        return deckEnsureClient.ensure(viewerId)
                .onFailure().recoverWithItem(false)
                .flatMap(ensured -> Boolean.TRUE.equals(ensured)
                        ? redisReader.read(viewerId, offset, limit)
                        : Uni.createFrom().item(List.of()));
    }

    /**
     * Hydrate IDs into lean {@link DeckCardDto}s in deck order.
     * Cache-fresh cards are served locally; only misses are batch-fetched, mapped to the lean
     * shape, and cached. If that fetch fails (timeout / open circuit), stale cached cards are
     * served for whatever is available. Soft-deleted profiles are dropped during mapping.
     */
    private Uni<List<DeckCardDto>> hydrate(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        Map<UUID, DeckCardDto> resolved = new HashMap<>();
        List<UUID> misses = new ArrayList<>();
        for (UUID id : ids) {
            DeckCardDto fresh = profileCache.getFresh(id);
            if (fresh != null) {
                resolved.put(id, fresh);
            } else {
                misses.add(id);
            }
        }

        if (misses.isEmpty()) {
            registry.counter("deckread.hydrate.fetch", "outcome", "all-cached").increment();
            return Uni.createFrom().item(assemble(ids, resolved));
        }

        Timer.Sample sample = Timer.start(registry);
        String idsParam = misses.stream().map(UUID::toString).collect(Collectors.joining(","));
        return profilesClient.getByIds(idsParam)
                .map(this::mapAndCache)
                .onItem().invoke(() ->
                        sample.stop(registry.timer("deckread.hydrate.fetch", "outcome", "success")))
                .onFailure().recoverWithItem(() -> {
                    sample.stop(registry.timer("deckread.hydrate.fetch", "outcome", "failure"));
                    return staleFallback(misses);
                })
                .map(fetchedById -> {
                    resolved.putAll(fetchedById);
                    return assemble(ids, resolved);
                });
    }

    /** Stale-while-revalidate: serve last-known cached cards for ids we could not fetch. */
    private Map<UUID, DeckCardDto> staleFallback(List<UUID> misses) {
        Map<UUID, DeckCardDto> stale = new HashMap<>();
        for (UUID id : misses) {
            DeckCardDto s = profileCache.getStale(id);
            if (s != null) {
                stale.put(id, s);
            }
        }
        if (!stale.isEmpty()) {
            registry.counter("deckread.hydrate.stale-served").increment(stale.size());
        }
        return stale;
    }

    /** Map fetched profiles to lean cards (dropping soft-deleted), caching each. */
    private Map<UUID, DeckCardDto> mapAndCache(List<SharedProfileDto> profiles) {
        Map<UUID, DeckCardDto> byId = new HashMap<>();
        for (SharedProfileDto p : profiles) {
            if (p.isDeleted()) {
                continue;
            }
            DeckCardDto card = toCard(p);
            profileCache.put(card);
            byId.put(card.profileId(), card);
        }
        return byId;
    }

    private DeckCardDto toCard(SharedProfileDto p) {
        List<DeckCardDto.Photo> photos = p.photos() == null ? List.of()
                : p.photos().stream()
                        .sorted(Comparator.comparingInt(SharedPhotoDto::position))
                        .map(ph -> new DeckCardDto.Photo(ph.url(), ph.position(), ph.isPrimary()))
                        .toList();
        return new DeckCardDto(p.id(), p.name(), p.age(), p.city(), p.bio(), photos, p.hobbies());
    }

    /** Assemble in deck order, dropping ids with no resolved card. */
    private List<DeckCardDto> assemble(List<UUID> ids, Map<UUID, DeckCardDto> resolved) {
        return ids.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
