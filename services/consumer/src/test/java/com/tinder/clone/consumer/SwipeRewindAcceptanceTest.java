package com.tinder.clone.consumer;

import com.tinder.clone.consumer.kafka.event.SwipeRevokedEvent;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.outbox.MatchOutboxService;
import com.tinder.clone.consumer.outbox.SwipeEventOutboxRepository;
import com.tinder.clone.consumer.outbox.SwipeOutboxService;
import com.tinder.clone.consumer.repository.PendingLikeRepository;
import com.tinder.clone.consumer.repository.SwipeRepository;
import com.tinder.clone.consumer.repository.SwipeRevocationRepository;
import com.tinder.clone.consumer.service.SwipeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Consumer compensating-event acceptance for FR-4, FR-6, FR-7, FR-9, NFR-3, RISK-1..RISK-3. */
@Tag("acceptance")
@ExtendWith(MockitoExtension.class)
@DisplayName("Feature: Consumer compensates read models and metrics on SwipeRevoked without deleting history (FR-4, FR-6, FR-7, FR-9)")
class SwipeRewindAcceptanceTest {

    @Mock
    private SwipeRepository swipeRepository;

    @Mock
    private PendingLikeRepository pendingLikeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private MatchOutboxService matchOutboxService;

    @Mock
    private SwipeOutboxService swipeOutboxService;

    @Mock
    private SwipeRevocationRepository revocationRepository;

    @Mock
    private SwipeEventOutboxRepository swipeOutboxRepository;

    private SimpleMeterRegistry meterRegistry;
    private SwipeService swipeService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        swipeService = new SwipeService(
                swipeRepository,
                pendingLikeRepository,
                redisTemplate,
                matchOutboxService,
                swipeOutboxService,
                revocationRepository,
                swipeOutboxRepository,
                meterRegistry
        );
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(swipeOutboxRepository.existsBySwiperIdAndSwipedIdAndEventIdNotAndCreatedAtAfter(any(), any(), any(), any())).thenReturn(false);
        lenient().when(revocationRepository.existsByRevokedSwipeEventId(any())).thenReturn(false);
    }

    @Test
    @DisplayName("Scenario: Given swipe-revoked topic contract, when consumer is configured, then app.kafka.topic.swipe-revoked is bound")
    void consumerConfiguresSwipeRevokedTopic() throws Exception {
        // Given
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        // When / Then
        assertThat(config).contains("swipe-revoked:");
    }

    @Test
    @DisplayName("Scenario: Given an unmatched like is revoked (FR-6, FR-7, FR-9), when SwipeRevoked arrives, then swipe_revocations records APPLIED, swiper decision resets to NULL, pending_likes is removed, Redis set evicts target, and metric increments")
    void revokingUnmatchedLikeCompensatesAllReadModelsAndMetrics() {
        // Given
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, true);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(false);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(true), any(), any(), eq("APPLIED"), any()))
                .thenReturn(1);
        when(redisTemplate.hasKey("swipes:exists:" + swiperId)).thenReturn(true);

        // When
        swipeService.revoke(event);

        // Then
        verify(swipeRepository).resetSwiperDecision(normalized.getProfile1Id(), normalized.getProfile2Id(), true);
        verify(pendingLikeRepository).deleteByPair(targetId, swiperId);
        verify(setOperations).remove("swipes:exists:" + swiperId, targetId.toString());
        assertThat(meterRegistry.counter("consumer_swipes_revoked_total", "outcome", "applied").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario: Given a pass against a profile that previously liked the viewer is revoked (FR-7), when SwipeRevoked arrives, then reciprocal pending like is restored")
    void revokingPassRestoresReciprocalPendingLikeWhenTargetPreviouslyLikedViewer() {
        // Given
        UUID swiperId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, false);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(false);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(false), any(), any(), eq("APPLIED"), any()))
                .thenReturn(1);
        when(swipeRepository.hasOtherSideLiked(normalized.getProfile1Id(), normalized.getProfile2Id(), false)).thenReturn(true);
        when(redisTemplate.hasKey("swipes:exists:" + swiperId)).thenReturn(false);

        // When
        swipeService.revoke(event);

        // Then
        verify(swipeRepository).resetSwiperDecision(normalized.getProfile1Id(), normalized.getProfile2Id(), false);
        verify(pendingLikeRepository).upsertIgnore(eq(swiperId), eq(targetId), any(Instant.class), eq(false));
        verify(pendingLikeRepository, never()).deleteByPair(any(), any());
    }

    @Test
    @DisplayName("Scenario: Given a race where mutual match already exists (FR-4, RISK-1), when SwipeRevoked arrives, then consumer records IGNORED_MATCH and does not undo the match")
    void defensiveMatchGuardPreventsUndoingMutualMatch() {
        // Given
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, true);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(true);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(true), any(), any(), eq("IGNORED_MATCH"), any()))
                .thenReturn(1);

        // When
        swipeService.revoke(event);

        // Then
        verify(swipeRepository, never()).resetSwiperDecision(any(), any(), anyBoolean());
        verify(pendingLikeRepository, never()).deleteByPair(any(), any());
        verify(setOperations, never()).remove(any(), any());
        assertThat(meterRegistry.counter("consumer_swipes_revoked_total", "outcome", "ignored_match").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario: Given duplicate Kafka redelivery of the same SwipeRevoked event (NFR-3), when handled again, then consumer no-ops idempotently")
    void duplicateSwipeRevokedDeliveryIsNoOp() {
        // Given
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, true);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(false);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(true), any(), any(), eq("APPLIED"), any()))
                .thenReturn(0);

        // When
        swipeService.revoke(event);

        // Then
        verify(swipeRepository, never()).resetSwiperDecision(any(), any(), anyBoolean());
        verify(pendingLikeRepository, never()).deleteByPair(any(), any());
        assertThat(meterRegistry.counter("consumer_swipes_revoked_total", "outcome", "applied").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Scenario: Given a newer swipe already exists after revokedAt (RISK-3), when out-of-order SwipeRevoked arrives, then consumer records IGNORED_STALE and preserves the newer swipe")
    void staleSwipeRevokedDoesNotOverwriteNewerSwipe() {
        // Given
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, false);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(false);
        when(swipeOutboxRepository.existsBySwiperIdAndSwipedIdAndEventIdNotAndCreatedAtAfter(
                eq(swiperId), eq(targetId), eq(UUID.fromString(event.getRevokedSwipeEventId())), any()))
                .thenReturn(true);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(false), any(), any(), eq("IGNORED_STALE"), any()))
                .thenReturn(1);

        // When
        swipeService.revoke(event);

        // Then
        verify(swipeRepository, never()).resetSwiperDecision(any(), any(), anyBoolean());
        assertThat(meterRegistry.counter("consumer_swipes_revoked_total", "outcome", "ignored_stale").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario: Given SwipeRevoked was processed before the original SwipeCreated (RISK-3 regression), when the late SwipeCreated arrives, then save() skips mutating read models and outbox")
    void lateSwipeCreatedForAlreadyRevokedEventIsIgnored() {
        // Given
        UUID swipeEventId = UUID.randomUUID();
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(revocationRepository.existsByRevokedSwipeEventId(swipeEventId)).thenReturn(true);
        var lateSwipe = com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent.builder()
                .eventId(swipeEventId.toString())
                .profile1Id(swiperId.toString())
                .profile2Id(targetId.toString())
                .decision(true)
                .timestamp(1_795_643_650_000L)
                .build();

        // When
        swipeService.save(lateSwipe);

        // Then
        verify(swipeRepository, never()).upsertSwipe(any(), any(), anyBoolean(), anyBoolean());
        verify(swipeOutboxService, never()).enqueueSwipeSaved(any());
        verify(matchOutboxService, never()).enqueue(any());
    }

    @Test
    @DisplayName("Scenario: Given a duplicate IGNORED_MATCH redelivery (NFR-3 regression), when insertIgnore returns 0, then ignored_match counter is not double-counted")
    void duplicateIgnoredMatchRedeliveryDoesNotDoubleCountMetric() {
        // Given
        UUID swiperId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SwipeRecordId normalized = SwipeRecordId.normalized(swiperId, targetId);
        SwipeRevokedEvent event = sampleEvent(swiperId, targetId, true);

        when(swipeRepository.isMutualMatch(normalized.getProfile1Id(), normalized.getProfile2Id())).thenReturn(true);
        when(revocationRepository.insertIgnore(any(), any(), eq(swiperId), eq(targetId), eq(true), any(), any(), eq("IGNORED_MATCH"), any()))
                .thenReturn(0);

        // When
        swipeService.revoke(event);

        // Then
        assertThat(meterRegistry.counter("consumer_swipes_revoked_total", "outcome", "ignored_match").count()).isEqualTo(0.0);
    }

    private SwipeRevokedEvent sampleEvent(UUID swiperId, UUID targetId, boolean originalDecision) {
        return SwipeRevokedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .revokedSwipeEventId(UUID.randomUUID().toString())
                .profile1Id(swiperId.toString())
                .profile2Id(targetId.toString())
                .originalDecision(originalDecision)
                .swipedAt(1_795_643_650_000L)
                .revokedAt(1_795_643_712_410L)
                .build();
    }
}
