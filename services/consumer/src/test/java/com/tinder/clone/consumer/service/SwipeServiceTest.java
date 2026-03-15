package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.outbox.MatchOutboxService;
import com.tinder.clone.consumer.repository.SwipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwipeServiceTest {

    @Mock
    private SwipeRepository repo;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private MatchOutboxService matchOutboxService;

    private SwipeService swipeService;

    @BeforeEach
    void setUp() {

        // Default: cache key is absent → no cache refresh (lenient to avoid UnnecessaryStubbingException
        // in tests that don't invoke the Redis cache path, e.g. existsBetweenBatch tests)
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    // ─── save(): match detection ───────────────────────────────────────────────

    @Test
    void publishesMatchCreatedEventWhenSwipesBecomeMutualLikes() {
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);
        long timestamp = 1735000000000L;

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1.toString())
                .profile2Id(profile2.toString())
                .decision(true)
                .timestamp(timestamp)
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, true);

        swipeService.save(event);

        verify(repo).upsertSwipe(
                normalizedId.getProfile1Id(),
                normalizedId.getProfile2Id(),
                true,
                true
        );
        ArgumentCaptor<MatchCreateEvent> matchEventCaptor = ArgumentCaptor.forClass(MatchCreateEvent.class);
        verify(matchOutboxService).enqueue(matchEventCaptor.capture());
        MatchCreateEvent enqueued = matchEventCaptor.getValue();

        assertEquals(normalizedId.getProfile1Id().toString(), enqueued.getProfile1Id());
        assertEquals(normalizedId.getProfile2Id().toString(), enqueued.getProfile2Id());
        assertEquals(Instant.ofEpochMilli(timestamp), enqueued.getCreatedAt());
        assertTrue(enqueued.getEventId() != null && !enqueued.getEventId().isBlank());
    }

    @Test
    void doesNotPublishMatchCreatedEventWhenMutualLikeNotReached() {
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1.toString())
                .profile2Id(profile2.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, false);

        swipeService.save(event);

        verify(matchOutboxService, never()).enqueue(any(MatchCreateEvent.class));
    }

    @Test
    void doesNotPublishMatch_whenLeftSwipe() {
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1.toString())
                .profile2Id(profile2.toString())
                .decision(false)   // left swipe (dislike)
                .timestamp(System.currentTimeMillis())
                .build();

        // isMutualMatch called once (pre-check), returns false → no second call needed
        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false);

        swipeService.save(event);

        // Upsert must still persist the dislike
        verify(repo).upsertSwipe(any(), any(), any(Boolean.class), eq(false));
        verify(matchOutboxService, never()).enqueue(any());
    }

    @Test
    void doesNotPublishSecondMatch_whenMatchAlreadyExisted() {
        // If isMutualMatch returns true BEFORE the upsert (wasMatchBefore = true),
        // the service must short-circuit and not enqueue a duplicate event.
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1.toString())
                .profile2Id(profile2.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        // Pre-check says match already exists (duplicate event)
        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(true);

        swipeService.save(event);

        // Upsert is still called (idempotent upsert), but no new match event
        verify(repo).upsertSwipe(any(), any(), any(Boolean.class), eq(true));
        verify(matchOutboxService, never()).enqueue(any());
    }

    @Test
    void usesNowAsCreatedAt_whenTimestampIsZero() {
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);
        Instant before = Instant.now();

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1.toString())
                .profile2Id(profile2.toString())
                .decision(true)
                .timestamp(0L)   // zero timestamp → should fall back to Instant.now()
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, true);

        swipeService.save(event);

        ArgumentCaptor<MatchCreateEvent> captor = ArgumentCaptor.forClass(MatchCreateEvent.class);
        verify(matchOutboxService).enqueue(captor.capture());

        assertThat(captor.getValue().getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void normalisesProfileOrder_regardlessOfEventOrder() {
        // profile2 > profile1 lexicographically, so normalized = (profile1, profile2)
        UUID profile1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profile2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(profile1, profile2);

        // Event where profile2 is the swiper (reversed order)
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile2.toString())   // swiper
                .profile2Id(profile1.toString())   // target
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, false);

        swipeService.save(event);

        // upsertSwipe must receive the normalised IDs, and swiperIsFirst = false
        // because profile2 (the swiper) is NOT profile1 in the normalized pair
        verify(repo).upsertSwipe(
                eq(normalizedId.getProfile1Id()),
                eq(normalizedId.getProfile2Id()),
                eq(false),    // swiper (profile2) is NOT first in normalized pair
                eq(true)
        );
    }

    // ─── save(): Redis cache refresh ──────────────────────────────────────────

    @Test
    void refreshesRedisCache_whenCacheKeyExists() {
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);
        String expectedCacheKey = "swipes:exists:" + swiperId;

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(swiperId.toString())
                .profile2Id(targetId.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, false);
        when(redisTemplate.hasKey(expectedCacheKey)).thenReturn(true);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        swipeService.save(event);

        verify(setOperations).remove(eq(expectedCacheKey), eq("EMPTY_MARKER"));
        verify(setOperations).add(eq(expectedCacheKey), eq(targetId.toString()));
        verify(redisTemplate).expire(eq(expectedCacheKey), any(Duration.class));
    }

    @Test
    void skipsRedisCacheRefresh_whenCacheKeyAbsent() {
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(swiperId.toString())
                .profile2Id(targetId.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))
                .thenReturn(false, false);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        swipeService.save(event);

        // opsForSet must never be touched
        verify(redisTemplate, never()).opsForSet();
    }

    // ─── existsBetweenBatch() ─────────────────────────────────────────────────

    @Test
    void existsBetweenBatch_returnsEmptyMap_whenCandidateListIsEmpty() {
        UUID viewerId = UUID.randomUUID();

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewerId, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void existsBetweenBatch_returnsTrueForSwipedCandidates_andFalseForUnswiped() {
        UUID viewerId = UUID.randomUUID();
        UUID swiped = UUID.randomUUID();
        UUID unswiped = UUID.randomUUID();

        when(repo.findViewerSwipedCandidates(eq(viewerId), any()))
                .thenReturn(Set.of(swiped));

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewerId, List.of(swiped, unswiped));

        assertThat(result).containsEntry(swiped, true);
        assertThat(result).containsEntry(unswiped, false);
    }

    @Test
    void existsBetweenBatch_returnsAllFalse_whenNoCandidatesWereSwiped() {
        UUID viewerId = UUID.randomUUID();
        List<UUID> candidates = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(repo.findViewerSwipedCandidates(eq(viewerId), any()))
                .thenReturn(Set.of());

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewerId, candidates);

        assertThat(result).hasSize(2);
        assertThat(result.values()).containsOnly(false);
    }
}
