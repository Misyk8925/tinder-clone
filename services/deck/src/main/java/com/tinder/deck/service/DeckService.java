package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.service.pipeline.DeckPipeline;
import com.tinder.deck.kafka.producer.DeckBuiltEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private DeckBuiltEventPublisher deckBuiltEvents;

    @Value("${deck.ttl-minutes:60}")
    private long ttlMinutes;

    public Mono<Void> rebuildOneDeck(SharedProfileDto viewer) {
        log.info("Rebuilding deck for viewer: {}", viewer.id());

        Instant start = Instant.now();

        return pipeline.buildDeck(viewer)
                .then(publishStableBuild(viewer.id()))
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

    private Mono<Void> publishStableBuild(UUID viewerId) {
        if (deckBuiltEvents == null) {
            return Mono.empty();
        }
        return Mono.zip(deckCache.getBuildInstant(viewerId), deckCache.size(viewerId).defaultIfEmpty(0L))
                .flatMap(tuple -> tuple.getT1()
                        .map(timestamp -> deckBuiltEvents.publish(
                                viewerId,
                                Long.toString(timestamp.toEpochMilli()),
                                Math.toIntExact(tuple.getT2())))
                        .orElseGet(Mono::empty))
                // The event is a repairable trigger; a completed Redis build remains authoritative.
                .onErrorResume(error -> Mono.empty());
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
                            .flatMap(viewer -> rebuildOneDeck(viewer)
                                    .then(deckCache.getBuildInstant(viewerId))
                                    .map(Optional::isPresent))
                            .defaultIfEmpty(false);
                });
    }

    private Mono<Boolean> hasFreshDeck(UUID viewerId, Duration ttl) {
        Instant now = Instant.now();

        return deckCache.getBuildInstant(viewerId)
                .map(buildInstant -> isFresh(buildInstant, now, ttl))
                .defaultIfEmpty(false);
    }

    private boolean isFresh(Optional<Instant> buildInstant, Instant now, Duration ttl) {
        return buildInstant
                .map(ts -> ts.plus(ttl).isAfter(now))
                .orElse(false);
    }
}
