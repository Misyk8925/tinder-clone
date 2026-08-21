package com.tinder.profiles.application.photos.model;

/** Result of storing photo bytes in the photos service. */
public record StoredPhotoMedia(
        String storageId,
        String originalKey,
        String originalUrl,
        String largeUrl,
        String mediumUrl,
        String smallUrl,
        String contentType,
        long size
) {
}
