package com.tinder.profiles.photos.media;

import java.util.UUID;

public record MediaUploadIntentResponse(
        UUID mediaId,
        MediaStatus status,
        String uploadKey,
        String uploadUrl
) {
}

