package com.tinder.profiles.application.profile.query;

import com.tinder.profiles.application.profile.model.PreferencesData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Transport-neutral profile snapshot used by internal profile queries. */
public record InternalProfileView(
        UUID id,
        String name,
        Integer age,
        String bio,
        String city,
        boolean active,
        LocationView location,
        PreferencesData preferences,
        boolean deleted,
        List<PhotoView> photos,
        List<String> hobbies
) {
    public record LocationView(
            UUID id,
            Double latitude,
            Double longitude,
            String city,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PhotoView(
            UUID photoId,
            UUID profileId,
            String s3Key,
            boolean primary,
            int position,
            String url,
            String contentType,
            long size,
            LocalDateTime createdAt
    ) {
    }
}
