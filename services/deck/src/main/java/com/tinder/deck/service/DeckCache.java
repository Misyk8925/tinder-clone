package com.tinder.deck.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeckCache {

    private final ReactiveStringRedisTemplate redis;

    private static String deckKey(UUID id)   { return "deck:" + id; }
    private static String deckTsKey(UUID id) { return "deck:build:ts:" + id; }


    public Mono<Void> writeDeck(UUID viewerId, List<Entry<UUID, Double>> deck, Duration ttl) {
        String key   = deckKey(viewerId);
        String tsKey = deckTsKey(viewerId);

        ReactiveZSetOperations<String, String> z = redis.opsForZSet();

        Mono<Long> addAll = Flux.fromIterable(deck)
                .map(e -> ZSetOperations.TypedTuple.of(e.getKey().toString(), e.getValue()))
                .collect(Collectors.toSet())
                .flatMap(tuples -> z.addAll(key, tuples));

        return redis.delete(key, tsKey)     // fast delete старых данных
                .then(addAll)                               // ZADD all
                .then(redis.expire(key, ttl))               // TTL
                .then(redis.opsForValue().set(tsKey, String.valueOf(System.currentTimeMillis()))) // TS
                .then();
    }

    public Flux<UUID> readDeck(UUID viewerId, int offset, int limit) {
        String key = deckKey(viewerId);
        long end = offset + Math.max(limit, 1) - 1;
        return redis.opsForZSet()
                .reverseRange(key, org.springframework.data.domain.Range.closed((long)offset, end))
                .map(UUID::fromString);
    }

    public Mono<Long> size(UUID viewerId) {
        return redis.opsForZSet().size(deckKey(viewerId));
    }

    public Mono<Optional<Instant>> getBuildInstant(UUID viewerId) {
        return redis.opsForValue().get(deckTsKey(viewerId))
                .map(v -> Optional.of(Instant.ofEpochMilli(Long.parseLong(v))))
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Long> invalidate(UUID viewerId) {
        return redis.delete(deckKey(viewerId), deckTsKey(viewerId));
    }

    public Mono<List<UUID>> readTop(UUID viewerId, int topN) {
        return readDeck(viewerId, 0, topN).collectList();
    }

    public Flux<Entry<UUID, Double>> readRangeWithScores(UUID viewerId, long start, long end) {
        return redis.opsForZSet()
                .reverseRangeWithScores(deckKey(viewerId), org.springframework.data.domain.Range.closed(start, end))
                .map(t -> Map.entry(UUID.fromString(Objects.requireNonNull(t.getValue())),
                        Objects.requireNonNull(t.getScore())));
    }
}