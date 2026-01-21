package com.tinder.deck.service.pipeline;

import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// services/deck/src/main/java/com/tinder/deck/pipeline/DeckPipeline.java
@Component
@RequiredArgsConstructor
@Slf4j
public class DeckPipeline {

    private final CandidateSearchStage searchStage;
    private final SwipeFilterStage filterStage;
    private final ScoringStage scoringStage;
    private final CacheStage cacheStage;

    @Value("${deck.per-user-limit:500}")
    private int perUserLimit;

    public Mono<Void> buildDeck(SharedProfileDto viewer) {
        log.info("Starting deck build pipeline for viewer {}", viewer.id());

        return searchStage.searchCandidates(viewer)
                .transform(candidates -> filterStage.filterBySwipeHistory(viewer, candidates))
                .transform(filtered -> scoringStage.scoreAndRank(viewer, filtered))
                .take(perUserLimit)
                .collectList()
                .flatMap(rankedList -> {
                    if (rankedList.isEmpty()) {
                        log.info("No candidates to cache for viewer {}", viewer.id());
                        return Mono.empty();
                    }
                    return cacheStage.cacheDeck(viewer.id(), Flux.fromIterable(rankedList));
                });
    }

}