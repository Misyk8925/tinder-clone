package com.tinder.profiles.photos.media;

import java.util.UUID;

public record MediaAssetResponse(
        UUID mediaId,
        MediaStatus status,
        String ownerType,
        String ownerId,
        String contentType,
        Long sizeBytes,
        String originalKey,
        String largeKey,
        String mediumKey,
        String smallKey,
        String originalUrl,
        String largeUrl,
        String mediumUrl,
        String smallUrl,
        String errorMessage
) {
}

