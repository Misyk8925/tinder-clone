package com.tinder.profiles.application.photos.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** A catalogued photo: its identity, slot and the key of its original object. */
public record StoredPhoto(
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
