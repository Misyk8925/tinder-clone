package com.tinder.swipes.repository;

import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SwipeRepository extends JpaRepository<SwipeRecord, SwipeRecordId> {
    SwipeRecord findBySwipeRecordId(SwipeRecordId embeddedId);

    @Query("""
       SELECT CASE
                WHEN s.swipeRecordId.profile1Id = :viewerId THEN s.swipeRecordId.profile2Id
                ELSE s.swipeRecordId.profile1Id
              END
       FROM SwipeRecord s
       WHERE (s.swipeRecordId.profile1Id = :viewerId AND s.swipeRecordId.profile2Id IN :candidateIds)
          OR (s.swipeRecordId.profile2Id = :viewerId AND s.swipeRecordId.profile1Id IN :candidateIds)
       """)
    Set<UUID> findAnyDirection(@Param("viewerId") UUID viewerId,
                               @Param("candidateIds") List<UUID> candidateIds);
}

