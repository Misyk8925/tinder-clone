package com.tinder.clone.consumer.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwipeRevokedEvent {
    private String eventId;
    private String revokedSwipeEventId;
    private String profile1Id;
    private String profile2Id;
    private boolean originalDecision;
    private long swipedAt;
    private long revokedAt;
}
