package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
 import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class DeckService {

    private final ProfilesHttp profilesHttp;
    private final SwipesHttp swipesHttp;
    private final DeckCache cache;
    private final ScoringService scoring;

    @Value("${deck.parallelism:32}")     private int parallelism;
    @Value("${deck.request-timeout-ms}") private long timeoutMs;
    @Value("${deck.retries:1}")          private long retries;
    @Value("${deck.ttl-minutes:60}")     private long ttlMin;
    @Value("${deck.per-user-limit:500}") private int perUserLimit;
    @Value("${deck.search-limit:2000}")  private int searchLimit;

    private static final Logger log = LoggerFactory.getLogger(DeckService.class);

    public Mono<Void> rebuildOneDeck(SharedProfileDto viewer) {
        SharedPreferencesDto prefs = viewer.preferences();
        
        // Use default preferences if null
        if (prefs == null) {
            log.warn("Viewer {} has null preferences, using defaults", viewer.id());
            prefs = new SharedPreferencesDto(18, 50, "ANY", 100);
        }
        
        final SharedPreferencesDto finalPrefs = prefs;
        final long start = System.currentTimeMillis();
        log.info("Rebuild deck started for viewer {} with preferences: gender={}, age={}-{}", 
                viewer.id(), finalPrefs.gender(), finalPrefs.minAge(), finalPrefs.maxAge());

        // 1) Candidates from Profiles by filters
        Flux<SharedProfileDto> candidates = profilesHttp
                .searchProfiles(
                        viewer.id(),
                        finalPrefs, searchLimit
                )
                .doOnSubscribe(s -> log.debug("Search profiles subscribed for viewer {}", viewer.id()))
                .doOnNext(c -> log.debug("Received candidate {} for viewer {}", c.id(), viewer.id()))
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("Profiles search failed for viewer {}: {}", viewer.id(), e.toString()))
                .retry(retries)
                .onErrorResume(e -> Flux.empty());

        // 2) Batch check of swipes (glue into batches of 200, for example)
        Flux<SharedProfileDto> filtered = candidates
                .buffer(200)
                .concatMap(batch -> {
                    List<UUID> ids = batch.stream().map(SharedProfileDto::id).toList();
                    log.debug("Processing swipe batch of size {} for viewer {}", ids.size(), viewer.id());
                    return swipesHttp.betweenBatch(viewer.id(), ids)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .retry(retries)
                            .doOnError(e -> log.warn("Swipes batch failed for viewer {} batchSize {}: {}", viewer.id(), ids.size(), e.toString()))
                            .onErrorReturn(Collections.emptyMap())
                            .flatMapMany(map -> {
                                log.debug("Swipe map returned {} entries for viewer {}", map.size(), viewer.id());
                                return Flux.fromIterable(batch)
                                        .filter(c -> !map.getOrDefault(c.id(), false));
                            }); // false => no record — candidate is good
                }, 1); // sequential processing of batches (so as not to blow up Swipes)

        // 3) Scoring + sorting + limiting
        return filtered
                .doOnNext(c -> log.debug("Filtered candidate {} for viewer {}", c.id(), viewer.id()))
                .parallel(parallelism).runOn(Schedulers.parallel())
                .map(c -> Map.entry(c.id(), scoring.score(viewer, c)))
                .sequential()
                .sort(Comparator.comparingDouble(Map.Entry<UUID, Double>::getValue).reversed())
                .take(perUserLimit)
                .collectList()
                // 4) Write to Redis ZSET
                .flatMap(deck -> {
                    log.info("Deck prepared for viewer {} with size {}. Writing to cache...", viewer.id(), deck.size());
                    if (deck.isEmpty()) {
                        log.warn("Empty deck for viewer {} - no candidates found after filtering", viewer.id());
                    }
                    return cache.writeDeck(viewer.id(), deck, Duration.ofMinutes(ttlMin))
                            .doOnSuccess(v -> log.info("Deck written for viewer {} in {} ms", viewer.id(), System.currentTimeMillis() - start));
                })
                .doOnError(e -> log.error("Failed to rebuild deck for viewer {}: {}", viewer.id(), e.toString()));
    }
}
