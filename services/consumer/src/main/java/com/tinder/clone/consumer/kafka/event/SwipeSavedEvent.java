package com.tinder.clone.consumer.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeSavedEvent {

    private String eventId;
    private String profile1Id;
    private String profile2Id;
    private boolean decision;
    private long timestamp;
}
