package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.AbstractIntegrationTest;
import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.model.SwipeRecord;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.outbox.MatchEventOutboxRepository;
import com.tinder.clone.consumer.outbox.model.MatchEventOutbox;
import com.tinder.clone.consumer.repository.SwipeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SwipeService}.
 * Uses real PostgreSQL + Redis containers (via {@link AbstractIntegrationTest}) to verify
 * the complete swipe-processing pipeline: persistence, mutual-match detection, outbox enqueueing,
 * Redis cache refresh, and idempotency.
 */
class SwipeServiceIntegrationTest extends AbstractIntegrationTest {

    // Prevent Kafka listeners from starting — we drive the service directly
    @MockitoBean
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private SwipeService swipeService;

    @Autowired
    private SwipeRepository swipeRepository;

    @Autowired
    private MatchEventOutboxRepository outboxRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void cleanUp() {
        swipeRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    @Test
    void save_persistsSwipeRecord_forRightSwipe() {
        UUID swiperId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);

        swipeService.save(buildEvent(swiperId, targetId, true));

        Optional<SwipeRecord> record = swipeRepository.findById(normalizedId);
        assertThat(record).isPresent();

        // Determine which decision column belongs to the swiper
        SwipeRecord saved = record.get();
        if (swiperId.equals(normalizedId.getProfile1Id())) {
            assertThat(saved.getDecision1()).isTrue();
            assertThat(saved.getDecision2()).isNull();
        } else {
            assertThat(saved.getDecision2()).isTrue();
            assertThat(saved.getDecision1()).isNull();
        }
    }

    @Test
    void save_persistsSwipeRecord_forLeftSwipe() {
        UUID swiperId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);

        swipeService.save(buildEvent(swiperId, targetId, false));

        Optional<SwipeRecord> record = swipeRepository.findById(normalizedId);
        assertThat(record).isPresent();

        SwipeRecord saved = record.get();
        if (swiperId.equals(normalizedId.getProfile1Id())) {
            assertThat(saved.getDecision1()).isFalse();
        } else {
            assertThat(saved.getDecision2()).isFalse();
        }
    }

    // ─── Mutual match detection ────────────────────────────────────────────────

    @Test
    void save_enqueuesMatchOutboxEvent_whenMutualLikeIsFormed() {
        UUID profile1 = UUID.randomUUID();
        UUID profile2 = UUID.randomUUID();

        // First swipe: profile1 → profile2 (right)
        swipeService.save(buildEvent(profile1, profile2, true));
        assertThat(outboxRepository.count()).isZero();

        // Second swipe: profile2 → profile1 (right) → mutual match
        swipeService.save(buildEvent(profile2, profile1, true));

        List<MatchEventOutbox> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        MatchEventOutbox event = outbox.get(0);

        // Profiles are stored in normalised order; both IDs must be present
        assertThat(List.of(event.getProfile1Id().toString(), event.getProfile2Id().toString()))
                .containsExactlyInAnyOrder(profile1.toString(), profile2.toString());
        assertThat(event.getPublishedAt()).isNull(); // still pending
    }

    @Test
    void save_doesNotEnqueueMatchEvent_forOneSidedRightSwipe() {
        UUID profile1 = UUID.randomUUID();
        UUID profile2 = UUID.randomUUID();

        swipeService.save(buildEvent(profile1, profile2, true));

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void save_doesNotEnqueueMatchEvent_whenEitherSwipeIsLeft() {
        UUID profile1 = UUID.randomUUID();
        UUID profile2 = UUID.randomUUID();

        // profile1 likes, profile2 dislikes
        swipeService.save(buildEvent(profile1, profile2, true));
        swipeService.save(buildEvent(profile2, profile1, false));

        assertThat(outboxRepository.count()).isZero();
    }

    // ─── Idempotency ──────────────────────────────────────────────────────────

    @Test
    void save_isIdempotent_duplicateSwipeDoesNotCreateSecondMatchEvent() {
        UUID profile1 = UUID.randomUUID();
        UUID profile2 = UUID.randomUUID();

        // Form the mutual match
        swipeService.save(buildEvent(profile1, profile2, true));
        swipeService.save(buildEvent(profile2, profile1, true));
        assertThat(outboxRepository.count()).isEqualTo(1);

        // Replay the same event — must NOT create a second outbox row
        swipeService.save(buildEvent(profile2, profile1, true));

        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void save_isIdempotent_sameSwipeTwiceKeepsSingleRecord() {
        UUID swiperId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);

        swipeService.save(buildEvent(swiperId, targetId, true));
        swipeService.save(buildEvent(swiperId, targetId, true));

        assertThat(swipeRepository.findAll())
                .extracting(SwipeRecord::getSwipeRecordId)
                .containsOnly(normalizedId);
    }

    // ─── Redis cache refresh ──────────────────────────────────────────────────

    @Test
    void save_addsTargetToRedisCache_whenSwiperCacheKeyExists() {
        UUID swiperId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String cacheKey = "swipes:exists:" + swiperId;

        // Pre-warm the cache key so refreshSwipeCache finds it
        redisTemplate.opsForSet().add(cacheKey, "EMPTY_MARKER");

        swipeService.save(buildEvent(swiperId, targetId, true));

        // Target ID must be present in the set; EMPTY_MARKER must be removed
        assertThat(redisTemplate.opsForSet().isMember(cacheKey, targetId.toString())).isTrue();
        assertThat(redisTemplate.opsForSet().isMember(cacheKey, "EMPTY_MARKER")).isFalse();
        assertThat(redisTemplate.getExpire(cacheKey)).isGreaterThan(0);
    }

    @Test
    void save_doesNotCreateRedisKey_whenCacheKeyWasAbsent() {
        UUID swiperId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String cacheKey = "swipes:exists:" + swiperId;

        // No pre-existing cache key
        swipeService.save(buildEvent(swiperId, targetId, true));

        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
    }

    // ─── existsBetweenBatch() ─────────────────────────────────────────────────

    @Test
    void existsBetweenBatch_returnsTrueForSwipedCandidate_andFalseForUnswiped() {
        UUID viewer = UUID.randomUUID();
        UUID swiped = UUID.randomUUID();
        UUID unswiped = UUID.randomUUID();

        // viewer swipes on `swiped`
        swipeService.save(buildEvent(viewer, swiped, true));

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewer, List.of(swiped, unswiped));

        assertThat(result).containsEntry(swiped, true);
        assertThat(result).containsEntry(unswiped, false);
    }

    @Test
    void existsBetweenBatch_returnsAllFalse_whenViewerHasNotSwipedAnyone() {
        UUID viewer = UUID.randomUUID();
        List<UUID> candidates = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewer, candidates);

        assertThat(result).hasSize(3);
        assertThat(result.values()).containsOnly(false);
    }

    @Test
    void existsBetweenBatch_returnsEmptyMap_whenCandidateListIsEmpty() {
        UUID viewer = UUID.randomUUID();

        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(viewer, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void existsBetweenBatch_isTrueForBothDirections_whenBothSwipeEachOther() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        swipeService.save(buildEvent(a, b, true));
        swipeService.save(buildEvent(b, a, true));

        // From a's perspective: b is already swiped
        assertThat(swipeService.existsBetweenBatch(a, List.of(b))).containsEntry(b, true);
        // From b's perspective: a is already swiped
        assertThat(swipeService.existsBetweenBatch(b, List.of(a))).containsEntry(a, true);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SwipeCreatedEvent buildEvent(UUID swiperId, UUID targetId, boolean decision) {
        return SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(swiperId.toString())
                .profile2Id(targetId.toString())
                .decision(decision)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}



