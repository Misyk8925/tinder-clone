package com.tinder.match.match.implementation;

import com.tinder.match.match.NewMatchEvent;
import com.tinder.match.match.dto.MatchRequestDto;
import com.tinder.match.match.dto.MatchResponseDto;
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
import java.util.List;
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

        Instant matchedAt = event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now();

        Match match = Match.builder()
                .id(matchId)
                .status(MatchStatus.ACTIVE)
                .createdAt(matchedAt)
                .matchedAt(matchedAt)
                .updatedAt(matchedAt)
                .build();

        matchRepository.save(match);
        log.info("Created match profile1Id={} profile2Id={} matchedAt={}",
                matchId.getProfile1Id(),
                matchId.getProfile2Id(),
                matchedAt);

        NewMatchEvent newMatchEvent = new NewMatchEvent(
                UUID.randomUUID(),
                matchId.getProfile1Id(),
                matchId.getProfile2Id(),
                matchedAt
        );
        applicationEventPublisher.publishEvent(newMatchEvent);
    }

    @Override
    public List<MatchResponseDto> getMyMatches(UUID profileId) {
        return matchRepository.findByProfileId(profileId).stream()
                .map(m -> new MatchResponseDto(
                        m.getId().getProfile1Id() + "_" + m.getId().getProfile2Id(),
                        m.getId().getProfile1Id(),
                        m.getId().getProfile2Id(),
                        m.getMatchedAt()
                ))
                .toList();
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
                .matchedAt(matchedAt)
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
