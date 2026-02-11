package com.tinder.clone.consumer.repository;

import com.tinder.clone.consumer.model.SwipeRecord;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
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

    Set<UUID> findBySwipeRecordId_Profile1Id(UUID viewerId);


    @Query("""
       SELECT CASE
                WHEN s.swipeRecordId.profile1Id = :viewerId THEN s.swipeRecordId.profile2Id
                ELSE s.swipeRecordId.profile1Id
              END
       FROM SwipeRecord s
       WHERE (s.swipeRecordId.profile1Id = :viewerId AND s.decision1 IS NOT NULL)
          OR (s.swipeRecordId.profile2Id = :viewerId AND s.decision2 IS NOT NULL)
       """)
    Set<UUID> findProfilesViewerSwipedOn(@Param("viewerId") UUID viewerId);
}

