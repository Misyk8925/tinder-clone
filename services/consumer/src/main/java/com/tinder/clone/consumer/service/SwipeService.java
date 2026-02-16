package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.kafka.SwipeCreatedEvent;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.repository.SwipeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeService {

    private static final String SWIPE_KEY_PREFIX = "swipes:exists";
    private static final Duration SWIPE_CACHE_TTL = Duration.ofHours(24);

    private final SwipeRepository repo;

    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void save(SwipeCreatedEvent swipeRecord) {
        UUID swiperId = UUID.fromString(swipeRecord.getProfile1Id());
        UUID targetId = UUID.fromString(swipeRecord.getProfile2Id());
        SwipeRecordId normalizedId = SwipeRecordId.normalized(swiperId, targetId);
        boolean swiperIsFirst = swiperId.equals(normalizedId.getProfile1Id());

        repo.upsertSwipe(
                normalizedId.getProfile1Id(),
                normalizedId.getProfile2Id(),
                swiperIsFirst,
                swipeRecord.isDecision()
        );
        refreshSwipeCache(swiperId, targetId);
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

    @Transactional
    protected Map<UUID, Boolean> warmUpCacheAndReturn(UUID viewerId, List<UUID> candidateIds) {
        // Find all profiles that viewer has swiped on (outgoing swipes only, not incoming)
        Set<UUID> allSwipesInDb = repo.findProfilesViewerSwipedOn(viewerId);


        String cacheKey = SWIPE_KEY_PREFIX + ":" + viewerId;
        if (!allSwipesInDb.isEmpty()) {
            String[] idsToCache = allSwipesInDb.stream().map(UUID::toString).toArray(String[]::new);
            redisTemplate.opsForSet().add(cacheKey, idsToCache);

            redisTemplate.expire(cacheKey, SWIPE_CACHE_TTL);
        } else {
            // No swipes found, store EMPTY_MARKER
            redisTemplate.opsForSet().add(cacheKey, "EMPTY_MARKER");
            // TODO check time duration
            redisTemplate.expire(cacheKey, Duration.ofMinutes(5));
        }

        Map<UUID, Boolean> result = new HashMap<>();
        for (UUID cid : candidateIds) {
            result.put(cid, allSwipesInDb.contains(cid));
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
}
