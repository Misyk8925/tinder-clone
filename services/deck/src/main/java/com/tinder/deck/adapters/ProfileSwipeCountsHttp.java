package com.tinder.deck.adapters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Pages through the durable swipe history in the consumer service, over the existing mTLS
 * channel. Only a rebuild uses this; the live path never leaves deck.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileSwipeCountsHttp {

    /**
     * A page has to fit inside WebClient's 256 KB in-memory buffer. Ten thousand rows did
     * not — a live rebuild against twelve thousand real swipes failed with
     * {@code DataBufferLimitException} — and the client is shared with the deck build's hot
     * path, so raising its codec limit for a recovery job would be the wrong trade. A
     * thousand rows is roughly ninety kilobytes.
     */
    private static final int PAGE_SIZE = 1_000;

    private final WebClient swipesWebClient;

    /** Every profile that has ever been swiped on, in id order, a page at a time. */
    public Flux<ProfileSwipeCount> all() {
        return page(null).expand(counts -> counts.isEmpty()
                        ? Mono.empty()
                        : page(counts.get(counts.size() - 1).profileId()))
                .flatMapIterable(counts -> counts);
    }

    private Mono<List<ProfileSwipeCount>> page(String after) {
        return swipesWebClient.get()
                .uri(builder -> {
                    builder.path("/internal/profile-swipe-counts").queryParam("limit", PAGE_SIZE);
                    if (after != null) {
                        builder.queryParam("after", after);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProfileSwipeCount>>() {})
                .doOnNext(counts -> log.debug("Fetched {} profile swipe counts after {}", counts.size(), after));
    }

    public record ProfileSwipeCount(String profileId, long impressions, long likes) {
    }
}
