package com.tinder.profiles.infrastructure.external.media;

import java.util.UUID;

public record MediaUploadIntentResponse(
        UUID mediaId,
        MediaStatus status,
        String uploadKey,
        String uploadUrl
) {
}

