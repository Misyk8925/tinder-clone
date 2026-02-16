package com.tinder.clone.consumer.repository;

import com.tinder.clone.consumer.model.SwipeRecord;
import com.tinder.clone.consumer.model.embedded.SwipeRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SwipeRepository extends JpaRepository<SwipeRecord, SwipeRecordId> {

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

    @Query("""
       SELECT CASE
                WHEN s.swipeRecordId.profile1Id = :viewerId THEN s.swipeRecordId.profile2Id
                ELSE s.swipeRecordId.profile1Id
              END
       FROM SwipeRecord s
       WHERE (s.swipeRecordId.profile1Id = :viewerId
              AND s.swipeRecordId.profile2Id IN :candidateIds
              AND s.decision1 IS NOT NULL)
          OR (s.swipeRecordId.profile2Id = :viewerId
              AND s.swipeRecordId.profile1Id IN :candidateIds
              AND s.decision2 IS NOT NULL)
       """)
    Set<UUID> findViewerSwipedCandidates(@Param("viewerId") UUID viewerId,
                                         @Param("candidateIds") List<UUID> candidateIds);

    @Modifying
    @Query(value = """
    INSERT INTO swipe_records  (profile1_id, profile2_id, decision1, decision2, version)
    VALUES (:p1, :p2,
        CASE WHEN CAST(:swiperIsFirst AS boolean) THEN CAST(:decision AS boolean) ELSE NULL END,
        CASE WHEN NOT CAST(:swiperIsFirst AS boolean) THEN CAST(:decision AS boolean) ELSE NULL END,
        0
    )
    ON CONFLICT (profile1_id, profile2_id) DO UPDATE SET
        decision1 = COALESCE( -- returns a if a not null, b
            swipe_records.decision1,
            EXCLUDED.decision1
        ),
        decision2 = COALESCE(
            swipe_records.decision2,
            EXCLUDED.decision2
        ),
        version = swipe_records.version + 1
    """, nativeQuery = true)
    void upsertSwipe(
            @Param("p1") UUID p1,
            @Param("p2") UUID p2,
            @Param("swiperIsFirst") boolean swiperIsFirst,
            @Param("decision") boolean decision
    );
}
