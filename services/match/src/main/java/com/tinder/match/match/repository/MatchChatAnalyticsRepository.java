package com.tinder.match.match.repository;

import com.tinder.match.match.model.MatchChatAnalytics;
import com.tinder.match.match.model.MatchChatAnalyticsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchChatAnalyticsRepository extends JpaRepository<MatchChatAnalytics, MatchChatAnalyticsId> {

    Optional<MatchChatAnalytics> findByIdProfile1IdAndIdProfile2Id(UUID profile1Id, UUID profile2Id);

    List<MatchChatAnalytics> findByIdProfile1IdOrIdProfile2Id(UUID profile1Id, UUID profile2Id);

    default Optional<MatchChatAnalytics> findByParticipants(UUID firstProfileId, UUID secondProfileId) {
        MatchChatAnalyticsId id = MatchChatAnalyticsId.normalized(firstProfileId, secondProfileId);
        return findById(id);
    }
}
