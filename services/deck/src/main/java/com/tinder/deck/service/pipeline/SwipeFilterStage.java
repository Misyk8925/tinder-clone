package com.tinder.deck.service.pipeline;

import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// services/deck/src/main/java/com/tinder/deck/pipeline/SwipeFilterStage.java
@Component
@RequiredArgsConstructor
@Slf4j
public class SwipeFilterStage extends BasicStage {

    private final SwipesHttp swipesHttp;


    public Flux<SharedProfileDto> filterBySwipeHistory(
            SharedProfileDto viewer,
            Flux<SharedProfileDto> candidates) {

        log.debug("Filtering candidates by swipe history for viewer {}", viewer.id());

        return candidates
                .buffer(batchSize)
                .concatMap(batch -> filterBatch(viewer.id(), batch))
                .doOnComplete(() -> log.debug("Swipe filtering completed for viewer {}",
                        viewer.id()));
    }

    private Flux<SharedProfileDto> filterBatch(UUID viewerId, List<SharedProfileDto> batch) {
        List<UUID> candidateIds = batch.stream()
                .map(SharedProfileDto::id)
                .toList();

        log.trace("Processing swipe batch of size {} for viewer {}",
                candidateIds.size(), viewerId);

        return swipesHttp.betweenBatch(viewerId, candidateIds)
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retries)
                .onErrorReturn(Collections.emptyMap())
                .flatMapMany(swipeMap -> Flux.fromIterable(batch)
                        .filter(candidate -> !hasSwipeHistory(candidate.id(), swipeMap)));
    }

    private boolean hasSwipeHistory(UUID candidateId, Map<UUID, Boolean> swipeMap) {
        return swipeMap.getOrDefault(candidateId, false);
    }
}
