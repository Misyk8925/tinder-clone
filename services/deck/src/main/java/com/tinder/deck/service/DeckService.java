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

    public Mono<Void> rebuildOneDeck(SharedProfileDto viewer) {
        SharedPreferencesDto prefs = viewer.preferences();

        // 1) Кандидаты из Profiles по фильтрам
        Flux<SharedProfileDto> candidates = profilesHttp
                .searchProfiles(
                        viewer.id(),
                        prefs, searchLimit
                )
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retries)
                .onErrorResume(e -> Flux.empty());

        // 2) Батч-проверка свайпов (склеим в пачки по 200, например)
        Flux<SharedProfileDto> filtered = candidates
                .buffer(200)
                .concatMap(batch -> {
                    List<UUID> ids = batch.stream().map(SharedProfileDto::id).toList();
                    return swipesHttp.betweenBatch(viewer.id(), ids)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .retry(retries)
                            .onErrorReturn(Collections.emptyMap())
                            .flatMapMany(map -> Flux.fromIterable(batch)
                                    .filter(c -> !map.getOrDefault(c.id(), false))); // false => нет записи — кандидат годится
                }, 1); // последовательная обработка пачек (чтобы не взорвать Swipes)

        // 3) Скоринг + сортировка + ограничение
        return filtered
                .parallel(parallelism).runOn(Schedulers.parallel())
                .map(c -> Map.entry(c.id(), scoring.score(viewer, c)))
                .sequential()
                .sort(Comparator.comparingDouble(Map.Entry<UUID, Double>::getValue).reversed())
                .take(perUserLimit)
                .collectList()
                // 4) Запись в Redis ZSET
                .flatMap(deck -> cache.writeDeck(viewer.id(), deck, Duration.ofMinutes(ttlMin)));
    }
}