package com.tinder.profiles.profile.dto.profileData;

import java.time.LocalDateTime;
import java.util.UUID;

public record SharedLocationDto(
        UUID id,
        Double latitude,
        Double longitude,
        String city,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}