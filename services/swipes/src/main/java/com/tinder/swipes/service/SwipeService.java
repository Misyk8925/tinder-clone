package com.tinder.swipes.service;

import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import com.tinder.swipes.repository.SwipeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SwipeService {

    private final SwipeRepository repo;

    public SwipeService(SwipeRepository repo) {
        this.repo = repo;
    }

    public void save(SwipeRecordDto swipeRecord) {

        UUID profile1Id = UUID.fromString(swipeRecord.profile1Id());
        UUID profile2Id = UUID.fromString(swipeRecord.profile2Id());
        SwipeRecordId embeddedId = new SwipeRecordId(profile1Id, profile2Id);
        SwipeRecord swipeRecordEntity = repo.findBySwipeRecordId(embeddedId);
        if (swipeRecordEntity== null){
            System.out.println("RECORD IS NULL");
            SwipeRecord newSwipeRecord = new SwipeRecord(
                    embeddedId,
                    swipeRecord.decision(),
                    null
            );
            repo.save(newSwipeRecord);
        }
        else {
            System.out.println("RECORD IS NOT NULL");
            System.out.println("decision 2" + swipeRecordEntity.getDecision2());
            if (swipeRecordEntity.getDecision2() == null){
                System.out.println(swipeRecord.decision());
                Boolean decision = (Boolean) swipeRecord.decision();
                System.out.println(decision);
                SwipeRecord newSwipeRecord = new SwipeRecord(
                        embeddedId,
                        swipeRecordEntity.getDecision1(),
                        swipeRecord.decision()
                );
                System.out.println(newSwipeRecord.getDecision2());
                repo.save(newSwipeRecord);
            }
            else {
                System.out.println("decision 2 is not null");
            }
        }
    }
}
