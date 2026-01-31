package com.tinder.deck.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SharedPhotoDto(
        UUID photoId,
        UUID profileId,
        String s3Key,
        boolean isPrimary,
        int position,
        String url,
        String contentType,
        long size,
        LocalDateTime createdAt
) {

}
