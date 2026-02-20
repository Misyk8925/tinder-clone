package com.tinder.match.conversation.dto;

public record MessageAttachmentDto(
        String url,
        String mimeType,
        Long sizeBytes,
        String originalName,
        Integer width,
        Integer height,
        Long durationMs
) {
}
