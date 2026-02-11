package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.kafka.SwipeCreatedEvent;
import com.tinder.clone.consumer.model.SwipeRecord;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import com.tinder.clone.consumer.repository.SwipeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeService {

    private final SwipeRepository repo;

    @Transactional
    public void save(SwipeCreatedEvent swipeRecord) {
        log.info("!!!!!!!!!!!!!!!!!!!!!!!!!");
        UUID swiperId = UUID.fromString(swipeRecord.getProfile1Id());
        UUID targetId = UUID.fromString(swipeRecord.getProfile2Id());

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
                    .decision1(swipeRecord.isDecision())
                    .build();
            repo.save(newSwipeRecord);
        } else {
            // Record exists - update decision2 if not set

            log.info("record found, updating existing one");

            if (existing.getDecision2() == null) {
                log.info("decision 2 is null, updating it");
                Boolean decision = swipeRecord.isDecision();

                existing.setDecision2(decision);
                log.info("Updated decision 2: {}", existing.getDecision2());
                repo.save(existing);
            } else {
                log.info("decision 2 is not null");
            }
        }
    }
}
