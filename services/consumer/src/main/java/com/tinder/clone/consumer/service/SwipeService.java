package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.kafka.event.SwipeRevokedEvent;
import com.tinder.clone.consumer.model.dto.LikedMeDto;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.outbox.MatchOutboxService;
import com.tinder.clone.consumer.outbox.SwipeEventOutboxRepository;
import com.tinder.clone.consumer.outbox.SwipeOutboxService;
import com.tinder.clone.consumer.repository.PendingLikeRepository;
import com.tinder.clone.consumer.repository.SwipeRepository;
import com.tinder.clone.consumer.repository.SwipeRevocationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class SwipeService {

    private static final String SWIPE_KEY_PREFIX = "swipes:exists";
    private static final Duration SWIPE_CACHE_TTL = Duration.ofHours(24);

    private final SwipeRepository repo;
    private final PendingLikeRepository pendingLikeRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchOutboxService matchOutboxService;
    private final SwipeOutboxService swipeOutboxService;
    private final SwipeRevocationRepository revocationRepo;
    private final SwipeEventOutboxRepository swipeOutboxRepo;
    private final MeterRegistry meterRegistry;

    @Autowired
    public SwipeService(
            SwipeRepository repo,
            PendingLikeRepository pendingLikeRepo,
            RedisTemplate<String, Object> redisTemplate,
            MatchOutboxService matchOutboxService,
            SwipeOutboxService swipeOutboxService,
            SwipeRevocationRepository revocationRepo,
            SwipeEventOutboxRepository swipeOutboxRepo,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.repo = repo;
        this.pendingLikeRepo = pendingLikeRepo;
        this.redisTemplate = redisTemplate;
        this.matchOutboxService = matchOutboxService;
        this.swipeOutboxService = swipeOutboxService;
        this.revocationRepo = revocationRepo;
        this.swipeOutboxRepo = swipeOutboxRepo;
        this.meterRegistry = meterRegistry;
    }

    /** Backwards-compatible constructor for existing unit tests that do not exercise rewind. */
    public SwipeService(
            SwipeRepository repo,
            PendingLikeRepository pendingLikeRepo,
            RedisTemplate<String, Object> redisTemplate,
            MatchOutboxService matchOutboxService,
            SwipeOutboxService swipeOutboxService
    ) {
        this(repo, pendingLikeRepo, redisTemplate, matchOutboxService, swipeOutboxService, null, null, null);
    }

    @Transactional
    public void save(SwipeCreatedEvent swipeRecord) {
        if (revocationRepo != null && swipeRecord.getEventId() != null && !swipeRecord.getEventId().isBlank()) {
            UUID swipeEventId = UUID.fromString(swipeRecord.getEventId());
            if (revocationRepo.existsByRevokedSwipeEventId(swipeEventId)) {
                log.info("Skipping late SwipeCreated eventId={} because it was already revoked", swipeEventId);
                return;
            }
        }
        UUID swiperId = UUID.fromString(swipeRecord.getProfile1Id());
        UUID targetId = UUID.fromString(swipeRecord.getProfile2Id());
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);
        boolean swiperIsFirst = swiperId.equals(normalizedId.getProfile1Id());
        boolean wasMatchBefore = Boolean.TRUE.equals(
                repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id())
        );

        repo.upsertSwipe(
                normalizedId.getProfile1Id(),
                normalizedId.getProfile2Id(),
                swiperIsFirst,
                swipeRecord.isDecision()
        );
        refreshSwipeCache(swiperId, targetId);
        swipeOutboxService.enqueueSwipeSaved(swipeRecord);

        // swiper is now responding to targetId's possible previous like — clear it
        pendingLikeRepo.deleteByPair(swiperId, targetId);

        if (!swipeRecord.isDecision() || wasMatchBefore) {
            return;
        }

        boolean isMatchNow = Boolean.TRUE.equals(
                repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id())
        );
        if (isMatchNow) {
            enqueueMatchCreated(normalizedId, swipeRecord.getTimestamp());
        } else {
            // Right swipe, no match yet — notify target that swiper liked them
            pendingLikeRepo.upsertIgnore(targetId, swiperId, Instant.now(), Boolean.TRUE.equals(swipeRecord.getIsSuper()));
        }
    }

    @Transactional
    public void revoke(SwipeRevokedEvent event) {
        UUID eventId = UUID.fromString(event.getEventId());
        UUID revokedSwipeEventId = UUID.fromString(event.getRevokedSwipeEventId());
        UUID swiperId = UUID.fromString(event.getProfile1Id());
        UUID targetId = UUID.fromString(event.getProfile2Id());
        Instant swipedAt = Instant.ofEpochMilli(event.getSwipedAt());
        Instant revokedAt = Instant.ofEpochMilli(event.getRevokedAt());

        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);
        boolean swiperIsFirst = swiperId.equals(normalizedId.getProfile1Id());

        if (Boolean.TRUE.equals(repo.isMutualMatch(normalizedId.getProfile1Id(), normalizedId.getProfile2Id()))) {
            int inserted = recordRevocation(eventId, revokedSwipeEventId, swiperId, targetId, event.isOriginalDecision(), swipedAt, revokedAt, "IGNORED_MATCH");
            if (inserted > 0) {
                incrementRevocationMetric("ignored_match");
            }
            log.info("Ignored SwipeRevoked eventId={} because pair is already matched", eventId);
            return;
        }

        if (swipeOutboxRepo != null && swipeOutboxRepo.existsBySwiperIdAndSwipedIdAndEventIdNotAndCreatedAtAfter(
                swiperId, targetId, revokedSwipeEventId, revokedAt)) {
            int inserted = recordRevocation(eventId, revokedSwipeEventId, swiperId, targetId, event.isOriginalDecision(), swipedAt, revokedAt, "IGNORED_STALE");
            if (inserted > 0) {
                incrementRevocationMetric("ignored_stale");
            }
            log.info("Ignored stale SwipeRevoked eventId={} because a newer swipe already exists", eventId);
            return;
        }

        int inserted = recordRevocation(eventId, revokedSwipeEventId, swiperId, targetId, event.isOriginalDecision(), swipedAt, revokedAt, "APPLIED");
        if (inserted == 0) {
            log.debug("Duplicate SwipeRevoked eventId={} skipped idempotently", eventId);
            return;
        }

        repo.resetSwiperDecision(normalizedId.getProfile1Id(), normalizedId.getProfile2Id(), swiperIsFirst);

        if (event.isOriginalDecision()) {
            // Revoking a like removes it from target's "Likes You" list
            pendingLikeRepo.deleteByPair(targetId, swiperId);
        } else if (Boolean.TRUE.equals(repo.hasOtherSideLiked(normalizedId.getProfile1Id(), normalizedId.getProfile2Id(), swiperIsFirst))) {
            // Revoking a pass restores target's earlier incoming like in swiper's "Likes You" list
            pendingLikeRepo.upsertIgnore(swiperId, targetId, Instant.now(), false);
        }

        evictSwipeFromCache(swiperId, targetId);
        incrementRevocationMetric("applied");
        log.info("Applied SwipeRevoked eventId={} revokedSwipeEventId={} swiper={} target={}",
                eventId, revokedSwipeEventId, swiperId, targetId);
    }

    @Transactional
    public List<LikedMeDto> getLikedMe(UUID profileId) {
        log.info("Fetching 'liked me' list for profileId={}", profileId);
        return pendingLikeRepo.findByLikedUserIdOrderByLikedAtDesc(profileId)
                .stream()
                .map(p -> new LikedMeDto(p.getLikerProfileId(), p.getLikedAt(), p.isSuper()))
                .toList();
    }

    @Transactional
    public Map<UUID, Boolean> existsBetweenBatch(UUID viewerId, List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<UUID> swipedInBatch = repo.findViewerSwipedCandidates(viewerId, candidateIds);
        Map<UUID, Boolean> result = new HashMap<>(candidateIds.size());
        for (UUID cid : candidateIds) {
            result.put(cid, swipedInBatch.contains(cid));
        }
        return result;
    }

    private int recordRevocation(
            UUID eventId,
            UUID revokedSwipeEventId,
            UUID swiperId,
            UUID targetId,
            boolean originalDecision,
            Instant swipedAt,
            Instant revokedAt,
            String status
    ) {
        if (revocationRepo == null) {
            return 1;
        }
        return revocationRepo.insertIgnore(
                eventId, revokedSwipeEventId, swiperId, targetId,
                originalDecision, swipedAt, revokedAt, status, Instant.now()
        );
    }

    private void incrementRevocationMetric(String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter("consumer_swipes_revoked_total", "outcome", outcome).increment();
        }
    }

    private void refreshSwipeCache(UUID swiperId, UUID targetId) {
        String cacheKey = SWIPE_KEY_PREFIX + ":" + swiperId;
        Boolean cacheExists = redisTemplate.hasKey(cacheKey);

        if (!Boolean.TRUE.equals(cacheExists)) {
            return;
        }

        redisTemplate.opsForSet().remove(cacheKey, "EMPTY_MARKER");
        redisTemplate.opsForSet().add(cacheKey, targetId.toString());
        redisTemplate.expire(cacheKey, SWIPE_CACHE_TTL);
    }

    private void evictSwipeFromCache(UUID swiperId, UUID targetId) {
        String cacheKey = SWIPE_KEY_PREFIX + ":" + swiperId;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return;
        }
        redisTemplate.opsForSet().remove(cacheKey, targetId.toString());
    }

    private void enqueueMatchCreated(SwipeRecordId swipeRecordId, long swipeTimestamp) {
        Instant createdAt = swipeTimestamp > 0 ? Instant.ofEpochMilli(swipeTimestamp) : Instant.now();
        MatchCreateEvent matchEvent = MatchCreateEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(swipeRecordId.getProfile1Id().toString())
                .profile2Id(swipeRecordId.getProfile2Id().toString())
                .createdAt(createdAt)
                .build();

        log.info("Mutual match found. Enqueueing MatchCreateEvent for {} and {}",
                matchEvent.getProfile1Id(), matchEvent.getProfile2Id());
        matchOutboxService.enqueue(matchEvent);
    }
}
