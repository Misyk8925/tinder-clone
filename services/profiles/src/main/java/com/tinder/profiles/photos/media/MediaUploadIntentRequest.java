package com.tinder.profiles.photos.media;

public record MediaUploadIntentRequest(
        String ownerType,
        String ownerId,
        String uploaderUserId,
        String contentType,
        long sizeBytes
) {
}

