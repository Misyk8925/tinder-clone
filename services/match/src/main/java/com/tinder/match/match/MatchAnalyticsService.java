package com.tinder.match.match;

import com.tinder.match.match.kafka.MatchCreateEvent;

public interface MatchAnalyticsService {

    void addNewAnalytics(MatchCreateEvent event);
}
