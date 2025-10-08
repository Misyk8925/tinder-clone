package com.tinder.swipes.repository;

import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SwipeRepository extends JpaRepository<SwipeRecord, SwipeRecordId> {
    SwipeRecord findBySwipeRecordId(SwipeRecordId embeddedId);
}
