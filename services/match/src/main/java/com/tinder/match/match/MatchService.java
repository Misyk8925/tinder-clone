package com.tinder.match.match;

import com.tinder.match.match.dto.MatchRequestDto;
import com.tinder.match.match.kafka.MatchCreateEvent;

public interface MatchService {

     void create(MatchCreateEvent event);

     MatchRequestDto manualMatch();
}
