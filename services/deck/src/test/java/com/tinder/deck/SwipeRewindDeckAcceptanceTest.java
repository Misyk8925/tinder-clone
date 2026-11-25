package com.tinder.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.deck.DeckRedisKeys;
import com.tinder.contracts.dto.DeckEntry;
import com.tinder.contracts.event.v1.SwipeRevokedEventV1;
import com.tinder.deck.kafka.consumer.SwipeEventConsumer;
import com.tinder.deck.service.DeckCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deck Kafka consumer and Redis ZSET restore acceptance for FR-8 and NFR-3. */
@Tag("acceptance")
@DisplayName("Feature: Deck restores a rewound profile to position 0 in Redis ZSET (FR-8)")
class SwipeRewindDeckAcceptanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Scenario: Given swipe-revoked topic contract, when Deck is configured, then kafka.topics.swipe-revoked is declared")
    void deckConfiguresSwipeRevokedTopic() throws Exception {
        // Given
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        // When / Then
        assertThat(config).contains("swipe-revoked:");
    }

    @Test
    @DisplayName("Scenario: Given a non-empty deck with max score 50.0, when DeckCache.restoreToTop runs, then profile2Id is inserted as fresh with score 51.0 and added to reverse index")
    void deckCacheRestoresRewoundProfileAboveCurrentMaxScore() throws Exception {
        // Given
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveSetOperations<String, String> setOps = mock(ReactiveSetOperations.class);
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zsetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);

        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String deckKey = DeckRedisKeys.deck(viewerId);
        String containsKey = DeckRedisKeys.contains(targetId);

        when(setOps.isMember(DeckRedisKeys.DELETED_PROFILES, targetId.toString())).thenReturn(Mono.just(false));
        when(zsetOps.scan(eq(deckKey), any(ScanOptions.class))).thenReturn(Flux.empty());
        when(zsetOps.reverseRangeWithScores(eq(deckKey), any(Range.class)))
                .thenReturn(Flux.just(ZSetOperations.TypedTuple.of("{\"profileId\":\"other\",\"isSwiped\":false}", 50.0)));
        when(zsetOps.add(eq(deckKey), anyString(), eq(51.0))).thenReturn(Mono.just(true));
        when(redis.expire(eq(deckKey), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOps.add(containsKey, viewerId.toString())).thenReturn(Mono.just(1L));
        when(redis.expire(eq(containsKey), any(Duration.class))).thenReturn(Mono.just(true));

        DeckCache cache = new DeckCache(redis, objectMapper);

        // When
        Boolean restored = cache.restoreToTop(viewerId, targetId).block();

        // Then
        assertThat(restored).isTrue();
        String expectedMember = objectMapper.writeValueAsString(DeckEntry.fresh(targetId));
        verify(zsetOps).add(deckKey, expectedMember, 51.0);
        verify(setOps).add(containsKey, viewerId.toString());
    }

    @Test
    @DisplayName("Scenario: Given the target profile was deleted, when DeckCache.restoreToTop runs, then it skips insertion safely")
    void deckCacheSkipsDeletedProfileOnRewind() {
        // Given
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveSetOperations<String, String> setOps = mock(ReactiveSetOperations.class);
        @SuppressWarnings("unchecked")
        ReactiveZSetOperations<String, String> zsetOps = mock(ReactiveZSetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);

        UUID viewerId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        when(setOps.isMember(DeckRedisKeys.DELETED_PROFILES, deletedId.toString())).thenReturn(Mono.just(true));

        DeckCache cache = new DeckCache(redis, objectMapper);

        // When
        Boolean restored = cache.restoreToTop(viewerId, deletedId).block();

        // Then
        assertThat(restored).isFalse();
        verify(zsetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Scenario: Given a SwipeRevokedEventV1 arrives on Kafka, when SwipeEventConsumer handles it, then DeckCache.restoreToTop is invoked for (profile1Id, profile2Id)")
    void swipeEventConsumerDelegatesSwipeRevokedToDeckCache() {
        // Given
        DeckCache deckCache = mock(DeckCache.class);
        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(deckCache.restoreToTop(viewerId, targetId)).thenReturn(Mono.just(true));
        SwipeEventConsumer consumer = new SwipeEventConsumer(deckCache);
        SwipeRevokedEventV1 event = new SwipeRevokedEventV1(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                viewerId.toString(),
                targetId.toString(),
                false,
                1_795_643_650_000L,
                1_795_643_712_410L
        );

        // When
        consumer.consumeRevoked(event, 0, 1L);

        // Then
        verify(deckCache).restoreToTop(viewerId, targetId);
    }
}
