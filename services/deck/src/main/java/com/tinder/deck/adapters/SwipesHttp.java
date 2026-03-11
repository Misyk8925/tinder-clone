package com.tinder.deck.adapters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipesHttp {

    private final WebClient swipesWebClient;

    /**
     * Batch check if viewer has already swiped on each candidate.
     * Calls POST /between/batch on consumer service.
     * Returns map: candidateId -> true (already swiped) / false (not swiped).
     */
    public Mono<Map<UUID, Boolean>> betweenBatch(UUID viewerId, List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        log.debug("Calling /between/batch: viewerId={}, candidates={}", viewerId, candidateIds.size());
        return swipesWebClient.post()
                .uri("/between/batch?viewerId={id}", viewerId)
                .contentType(MediaType.APPLICATION_JSON)  // required by consumer controller
                .bodyValue(candidateIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<UUID, Boolean>>() {})
                .doOnNext(result -> log.debug(
                        "betweenBatch result: viewerId={}, checked={}, swiped={}",
                        viewerId, candidateIds.size(),
                        result.values().stream().filter(Boolean::booleanValue).count()));
    }
}
