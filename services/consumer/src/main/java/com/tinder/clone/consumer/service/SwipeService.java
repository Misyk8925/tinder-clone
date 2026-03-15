package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.model.dto.LikedMeDto;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.outbox.MatchOutboxService;
import com.tinder.clone.consumer.outbox.SwipeOutboxService;
import com.tinder.clone.consumer.repository.PendingLikeRepository;
import com.tinder.clone.consumer.repository.SwipeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeService {

    private static final String SWIPE_KEY_PREFIX = "swipes:exists";
    private static final Duration SWIPE_CACHE_TTL = Duration.ofHours(24);

    private final SwipeRepository repo;
    private final PendingLikeRepository pendingLikeRepo;

    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchOutboxService matchOutboxService;
    private final SwipeOutboxService swipeOutboxService;

    @Transactional
    public void save(SwipeCreatedEvent swipeRecord) {
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
            pendingLikeRepo.upsertIgnore(targetId, swiperId, Instant.now());
        }
    }

    @Transactional
    public List<LikedMeDto> getLikedMe(UUID profileId) {
        log.info("Fetching 'liked me' list for profileId={}", profileId);
        return pendingLikeRepo.findByLikedUserIdOrderByLikedAtDesc(profileId)
                .stream()
                .map(p -> new LikedMeDto(p.getLikerProfileId(), p.getLikedAt()))
                .toList();
    }

    /**
     * Returns map: candidateId -> true/false (has viewer already swiped on this candidate?)
     * true = viewer already swiped on this candidate → should NOT show in deck
     * false = viewer has not swiped on this candidate yet → can show in deck
     *
     * Note: This only checks OUTGOING swipes from viewer. We don't care if candidate swiped on viewer.
     */
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
