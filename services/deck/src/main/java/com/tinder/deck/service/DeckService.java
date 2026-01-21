package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.pipeline.DeckPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
 import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeckService {

    private final DeckPipeline pipeline;

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
}
