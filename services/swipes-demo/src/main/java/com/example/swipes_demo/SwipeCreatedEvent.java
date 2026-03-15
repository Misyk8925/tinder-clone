package com.example.swipes_demo;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isSuper")
    private boolean isSuper;
    private long timestamp;


}
