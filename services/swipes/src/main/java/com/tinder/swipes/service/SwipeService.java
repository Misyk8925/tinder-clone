package com.tinder.swipes.service;

import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import com.tinder.swipes.repository.SwipeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwipeService {

    private final SwipeRepository repo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final static String SWIPE_KEY_PREFIX = "swipes:exists";


    @Transactional
    public void save(SwipeRecordDto swipeRecord) {
        UUID swiperId = UUID.fromString(swipeRecord.profile1Id());
        UUID targetId = UUID.fromString(swipeRecord.profile2Id());

        // Try to find existing record in both directions
        SwipeRecordId id1 = new SwipeRecordId(swiperId, targetId);
        SwipeRecordId id2 = new SwipeRecordId(targetId, swiperId);

        SwipeRecord existing = repo.findBySwipeRecordId(id1);
        if (existing == null) {
            existing = repo.findBySwipeRecordId(id2);
        }

        if (existing == null) {
            // No existing record in either direction - create new one
            log.info("record not found, creating new one");
            SwipeRecord newSwipeRecord = SwipeRecord.builder()
                    .swipeRecordId(id1)
                    .decision1(swipeRecord.decision())
                    .build();
            repo.save(newSwipeRecord);
        } else {
            // Record exists - update decision2 if not set

            log.info("record found, updating existing one");

            if (existing.getDecision2() == null) {
                log.info("decision 2 is null, updating it");
                Boolean decision = swipeRecord.decision();

                existing.setDecision2(decision);
                log.info("Updated decision 2: {}", existing.getDecision2());
                repo.save(existing);
            } else {
                log.info("decision 2 is not null");
            }
        }
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

        String cacheKey = SWIPE_KEY_PREFIX + ":" + viewerId;

        // Check if cache exists for this viewer
        Boolean cacheExists = redisTemplate.hasKey(cacheKey);

        if (cacheExists) {
            // Cache exists, use it
            Set<Object> cachedMembers = redisTemplate.opsForSet().members(cacheKey);

            Map<UUID, Boolean> result = new HashMap<>();
            for (UUID cid : candidateIds) {
                // If EMPTY_MARKER exists and it's the only member, return false for all
                if (cachedMembers != null && cachedMembers.size() == 1 && cachedMembers.contains("EMPTY_MARKER")) {
                    result.put(cid, false);
                } else {
                    result.put(cid, cachedMembers != null && cachedMembers.contains(cid.toString()));
                }
            }
            return result;
        } else {
            // Cache doesn't exist, warm it up
            return warmUpCacheAndReturn(viewerId, candidateIds);
        }
    }

    @Transactional
    protected Map<UUID, Boolean> warmUpCacheAndReturn(UUID viewerId, List<UUID> candidateIds) {
        // Find all profiles that viewer has swiped on (outgoing swipes only, not incoming)
        Set<UUID> allSwipesInDb = repo.findProfilesViewerSwipedOn(viewerId);


        String cacheKey = SWIPE_KEY_PREFIX + ":" + viewerId;
        if (!allSwipesInDb.isEmpty()) {
            String[] idsToCache = allSwipesInDb.stream().map(UUID::toString).toArray(String[]::new);
            redisTemplate.opsForSet().add(cacheKey, idsToCache);

            redisTemplate.expire(cacheKey, Duration.ofHours(24));
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
}
