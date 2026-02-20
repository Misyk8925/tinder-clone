package com.tinder.clone.consumer.kafka.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeCreatedEvent {

    private String eventId;
    private String profile1Id;
    private String profile2Id;
    private boolean decision; // true = right swipe, false = left swipe
    private long timestamp;


}
