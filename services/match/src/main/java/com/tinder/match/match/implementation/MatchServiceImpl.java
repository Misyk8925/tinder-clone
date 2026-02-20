package com.tinder.match.match.implementation;

import com.tinder.match.match.NewMatchEvent;
import com.tinder.match.match.dto.MatchRequestDto;
import com.tinder.match.match.kafka.MatchCreateEvent;
import com.tinder.match.match.MatchService;
import com.tinder.match.match.model.Match;
import com.tinder.match.match.model.MatchId;
import com.tinder.match.match.model.MatchStatus;
import com.tinder.match.match.repository.MatchRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public void create(MatchCreateEvent event) {

        UUID profile1Id = UUID.fromString(event.getProfile1Id());
        UUID profile2Id = UUID.fromString(event.getProfile2Id());

        MatchId matchId = MatchId.normalized(profile1Id, profile2Id);

        Instant matchedAt = event.getCreatedAt();

        Match match = Match.builder()
                .id(matchId)
                .status(MatchStatus.ACTIVE)
                .createdAt(matchedAt)
                .updatedAt(matchedAt)
                .build();

        try {
            matchRepository.save(match);

            NewMatchEvent newMatchEvent = new NewMatchEvent(
                    UUID.randomUUID(),
                    matchId.getProfile1Id(),
                    matchId.getProfile2Id(),
                    matchedAt
            );
            applicationEventPublisher.publishEvent(newMatchEvent);
        } catch (Exception e) {
            log.error("Failed to save match: {}", match, e);
        }



    }

    @Override
    public MatchRequestDto manualMatch() {
        UUID profile1Id = UUID.randomUUID();
        UUID profile2Id = UUID.randomUUID();

        Instant matchedAt = Instant.now();
        MatchId matchId = MatchId.normalized(profile1Id, profile2Id);
        Match match = Match.builder()
                .id(matchId)
                .status(MatchStatus.ACTIVE)
                .createdAt(matchedAt)
                .updatedAt(matchedAt)
                .build();
        matchRepository.save(match);
        NewMatchEvent newMatchEvent = new NewMatchEvent(
                UUID.randomUUID(),
                matchId.getProfile1Id(),
                matchId.getProfile2Id(),
                matchedAt
        );
        applicationEventPublisher.publishEvent(newMatchEvent);
        return new MatchRequestDto(profile1Id, profile2Id);
    }
}
