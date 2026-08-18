package com.tinder.profiles.api.profile.dto.profileData;

import java.time.LocalDateTime;
import java.util.UUID;

/** API response shape for a profile photo. */
public record PhotoDto(
        UUID photoId,
        String s3Key,
        boolean primary,
        int position,
        String url,
        String contentType,
        long size,
        LocalDateTime createdAt
) {
}
