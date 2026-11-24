package com.tinder.deck.service.pipeline;

import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

// services/deck/src/main/java/com/tinder/deck/pipeline/CacheStage.java
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheStage {

    private final DeckCache deckCache;

    @Value("${deck.ttl-minutes:60}")
    private long ttlMinutes;

    @Value("${deck.empty-ttl-seconds:30}")
    private long emptyTtlSeconds = 30;

    public Mono<Void> cacheDeck(UUID viewerId, Flux<ScoringStage.ScoredCandidate> scoredCandidates) {
        log.debug("Caching deck for viewer {}", viewerId);

        return scoredCandidates
                .map(sc -> Map.entry(sc.candidateId(), sc.score()))
                .collectList()
                .flatMap(deck -> {
                    Duration ttl;
                    if (deck.isEmpty()) {
                        ttl = Duration.ofSeconds(Math.max(1L, emptyTtlSeconds));
                        log.info("Caching authoritative empty deck for viewer {} with short TTL {}s",
                                viewerId, ttl.toSeconds());
                    } else {
                        ttl = Duration.ofMinutes(ttlMinutes);
                    }
                    return deckCache.writeDeck(viewerId, deck, ttl);
                })
                .doOnSuccess(v -> log.info("Successfully cached all candidates for viewer {}",
                        viewerId))
                .doOnError(e -> log.error("Failed to cache deck for viewer {}: {}",
                        viewerId, e.getMessage()));
    }
}
