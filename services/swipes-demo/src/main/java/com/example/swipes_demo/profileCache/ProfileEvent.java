package com.example.swipes_demo.profileCache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileEvent {
    private String eventType; // ProfileCreated, ProfileDeleted, ProfileActivated
    private UUID profileId;
    private String userId;
    private Boolean isActive;
    private Boolean isDeleted;
    private Instant timestamp;

    // Для обработки out-of-order событий
    private Long sequenceNumber;
}
