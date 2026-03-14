package com.tinder.match.match;

import com.tinder.match.match.dto.MatchRequestDto;
import com.tinder.match.match.dto.MatchResponseDto;
import com.tinder.match.match.kafka.MatchCreateEvent;

import java.util.List;
import java.util.UUID;

public interface MatchService {

     void create(MatchCreateEvent event);

     MatchRequestDto manualMatch();

     List<MatchResponseDto> getMyMatches(UUID profileId);
}
