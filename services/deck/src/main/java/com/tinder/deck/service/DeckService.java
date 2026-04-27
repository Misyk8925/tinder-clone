package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.pipeline.DeckPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeckService {

    private final ProfilesHttp profilesHttp;
    private final DeckCache deckCache;
    private final DeckPipeline pipeline;

    @Value("${deck.ttl-minutes:60}")
    private long ttlMinutes;

    public Mono<Void> rebuildOneDeck(SharedProfileDto viewer) {
        log.info("Rebuilding deck for viewer: {}", viewer.id());

        Instant start = Instant.now();

        return pipeline.buildDeck(viewer)
                .doOnSuccess(v -> {
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    log.info("Deck rebuild completed for viewer {} in {}ms",
                            viewer.id(), duration);
                })
                .doOnError(e -> {
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    log.error("Deck rebuild failed for viewer {} after {}ms: {}",
                            viewer.id(), duration, e.getMessage());
                });
    }

    public Mono<Boolean> ensureDeck(UUID viewerId) {
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        return hasFreshDeck(viewerId, ttl)
                .flatMap(isFresh -> {
                    if (isFresh) {
                        return Mono.just(true);
                    }

                    return deckCache.withLock(viewerId, ensureDeckUnderLock(viewerId, ttl))
                            .defaultIfEmpty(false);
                });
    }

    private Mono<Boolean> ensureDeckUnderLock(UUID viewerId, Duration ttl) {
        return hasFreshDeck(viewerId, ttl)
                .flatMap(isFresh -> {
                    if (isFresh) {
                        return Mono.just(true);
                    }

                    return profilesHttp.getProfile(viewerId)
                            .flatMap(viewer -> rebuildOneDeck(viewer).then(deckCache.exists(viewerId)))
                            .defaultIfEmpty(false);
                });
    }

    private Mono<Boolean> hasFreshDeck(UUID viewerId, Duration ttl) {
        Instant now = Instant.now();

        return deckCache.size(viewerId)
                .flatMap(size -> {
                    if (size == null || size == 0) {
                        return Mono.just(false);
                    }

                    return deckCache.getBuildInstant(viewerId)
                            .map(buildInstant -> isFresh(buildInstant, now, ttl));
                })
                .defaultIfEmpty(false);
    }

    private boolean isFresh(Optional<Instant> buildInstant, Instant now, Duration ttl) {
        return buildInstant
                .map(ts -> ts.plus(ttl).isAfter(now))
                .orElse(false);
    }
}
