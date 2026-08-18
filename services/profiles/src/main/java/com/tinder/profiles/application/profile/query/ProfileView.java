package com.tinder.profiles.application.profile.query;

import com.tinder.profiles.application.profile.model.PreferencesData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transport-neutral profile details returned by the read use case. Built from
 * application types only, so inbound adapters never reach into the domain model.
 */
public record ProfileView(
        UUID profileId,
        String userId,
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        boolean active,
        LocalDateTime createdAt,
        PreferencesData preferences,
        List<PhotoView> photos,
        List<String> hobbies
) {
    public record PhotoView(
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
}
