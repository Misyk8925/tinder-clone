package com.tinder.deck.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwipesHttp {

    private final WebClient swipesWebClient;

    /**
     * Batch check if swipes exist between viewer and candidates
     * Calls /internal/between/batch endpoint (not secured)
     */
    public Mono<Map<UUID, Boolean>> betweenBatch(UUID viewerId, List<UUID> candidateIds) {
        return swipesWebClient.post()
                .uri("/between/batch?viewerId={id}", viewerId)
                .bodyValue(candidateIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<UUID, Boolean>>() {});
    }
}
