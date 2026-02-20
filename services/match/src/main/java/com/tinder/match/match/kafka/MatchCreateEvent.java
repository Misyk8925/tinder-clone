package com.tinder.match.match.kafka;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCreateEvent {

    private String eventId;
    private String profile1Id;
    private String profile2Id;
    private Instant createdAt;
}

