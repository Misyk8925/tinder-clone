package com.tinder.match.match.implementation;

import com.tinder.match.match.MatchAnalyticsService;
import com.tinder.match.match.kafka.MatchCreateEvent;
import com.tinder.match.match.model.MatchChatAnalytics;
import com.tinder.match.match.model.MatchChatAnalyticsId;
import com.tinder.match.match.repository.MatchChatAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MatchAnalyticsServiceImpl implements MatchAnalyticsService {

    private final MatchChatAnalyticsRepository repository;

    @Override
    public void addNewAnalytics(MatchCreateEvent event){

        UUID profile1Id = UUID.fromString(event.getProfile1Id());
        UUID profile2Id = UUID.fromString(event.getProfile2Id());

        MatchChatAnalyticsId analyticsId = MatchChatAnalyticsId.normalized(profile1Id, profile2Id);
        Instant matchedAt = event.getCreatedAt();

        MatchChatAnalytics analytics = MatchChatAnalytics.builder()
                .id(analyticsId)
                .matchedAt(matchedAt)
                .build();

        repository.save(analytics);
    }
}
