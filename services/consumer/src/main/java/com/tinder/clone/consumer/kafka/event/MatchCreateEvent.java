package com.tinder.clone.consumer.kafka.event;

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
