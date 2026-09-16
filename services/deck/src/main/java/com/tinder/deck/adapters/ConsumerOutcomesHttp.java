package com.tinder.deck.adapters;

import com.tinder.deck.experiment.ViewerOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads per-viewer swipe and match counts from the consumer service over the existing
 * mTLS channel. Consumer owns those events; deck owns the salt that turns them into arms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerOutcomesHttp {

    private final WebClient swipesWebClient;

    public Mono<List<ViewerOutcome>> outcomesBetween(Instant from, Instant to) {
        return swipesWebClient.get()
                .uri(builder -> builder.path("/internal/experiment-outcomes")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ViewerOutcomeResponse>>() {})
                .map(rows -> rows.stream()
                        .map(row -> new ViewerOutcome(
                                UUID.fromString(row.viewerId()), row.swipes(), row.likes(), row.matches()))
                        .toList())
                .doOnNext(rows -> log.debug("Experiment outcomes: {} viewers between {} and {}",
                        rows.size(), from, to));
    }

    private record ViewerOutcomeResponse(String viewerId, long swipes, long likes, long matches) {
    }
}
