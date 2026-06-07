package com.tinder.profiles.infrastructure.external.media;

public record MediaUploadIntentRequest(
        String ownerType,
        String ownerId,
        String uploaderUserId,
        String contentType,
        long sizeBytes
) {
}

