package com.tinder.swipes.service;

import com.tinder.swipes.kafka.SwipeCreatedEvent;
import com.tinder.swipes.kafka.producer.SwipeEventProducer;
import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import com.tinder.swipes.repository.SwipeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final SwipeEventProducer swipeEventProducer;
    private final static String SWIPE_KEY_PREFIX = "swipes:exists";
    private final static Duration SWIPE_CACHE_TTL = Duration.ofHours(24);

    @Value("${app.kafka.topic.swipe-created}")
    private String swipeCreatedTopic;


    @Transactional
    public void save(SwipeRecordDto swipeRecord) {
        log.info("SWIPE SERVICE: Saving swipe record: {}", swipeRecord);
        try {
            SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .profile1Id(swipeRecord.profile1Id())
                    .profile2Id(swipeRecord.profile2Id())
                    .decision(swipeRecord.decision())
                    .timestamp(new Timestamp(System.currentTimeMillis()).getTime())
                    .build();

            swipeEventProducer.sendSwipeEvent(event, swipeRecord.profile1Id(), swipeCreatedTopic);
        } catch (Exception e) {
            log.error("Error parsing timestamp for SwipeCreatedEvent: {}", e.getMessage(), e);
        }
//        UUID swiperId = UUID.fromString(swipeRecord.profile1Id());
//        UUID targetId = UUID.fromString(swipeRecord.profile2Id());
//
//        // Try to find existing record in both directions
//        SwipeRecordId id1 = new SwipeRecordId(swiperId, targetId);
//        SwipeRecordId id2 = new SwipeRecordId(targetId, swiperId);
//
//        SwipeRecord existing = repo.findBySwipeRecordId(id1);
//        if (existing == null) {
//            existing = repo.findBySwipeRecordId(id2);
//        }
//
//        if (existing == null) {
//            // No existing record in either direction - create new one
//            log.info("record not found, creating new one");
//            SwipeRecord newSwipeRecord = SwipeRecord.builder()
//                    .swipeRecordId(id1)
//                    .decision1(swipeRecord.decision())
//                    .build();
//            repo.save(newSwipeRecord);
//        } else {
//            // Record exists - update decision2 if not set
//
//            log.info("record found, updating existing one");
//
//            if (existing.getDecision2() == null) {
//                log.info("decision 2 is null, updating it");
//                Boolean decision = swipeRecord.decision();
//
//                existing.setDecision2(decision);
//                log.info("Updated decision 2: {}", existing.getDecision2());
//                repo.save(existing);
//            } else {
//                log.info("decision 2 is not null");
//            }
//        }
    }

    @Transactional
    public SwipeRecord saveDirectlyToDb(SwipeRecordDto swipeRecord) {
        UUID swiperId = UUID.fromString(swipeRecord.profile1Id());
        UUID targetId = UUID.fromString(swipeRecord.profile2Id());

        SwipeRecordId directId = new SwipeRecordId(swiperId, targetId);
        SwipeRecord directRecord = repo.findBySwipeRecordId(directId);

        if (directRecord != null) {
            if (directRecord.getDecision1() == null) {
                directRecord.setDecision1(swipeRecord.decision());
                SwipeRecord saved = repo.save(directRecord);
                refreshSwipeCache(swiperId, targetId);
                return saved;
            }
            refreshSwipeCache(swiperId, targetId);
            return directRecord;
        }

        SwipeRecordId reverseId = new SwipeRecordId(targetId, swiperId);
        SwipeRecord reverseRecord = repo.findBySwipeRecordId(reverseId);

        if (reverseRecord != null) {
            if (reverseRecord.getDecision2() == null) {
                reverseRecord.setDecision2(swipeRecord.decision());
                SwipeRecord saved = repo.save(reverseRecord);
                refreshSwipeCache(swiperId, targetId);
                return saved;
            }
            refreshSwipeCache(swiperId, targetId);
            return reverseRecord;
        }

        SwipeRecord newSwipeRecord = SwipeRecord.builder()
                .swipeRecordId(directId)
                .decision1(swipeRecord.decision())
                .build();

        SwipeRecord saved = repo.save(newSwipeRecord);
        refreshSwipeCache(swiperId, targetId);
        return saved;
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
