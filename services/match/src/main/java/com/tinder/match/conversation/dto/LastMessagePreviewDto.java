package com.tinder.match.conversation.dto;

import com.tinder.match.conversation.model.MessageType;

import java.time.Instant;
import java.util.UUID;

public record LastMessagePreviewDto(
        UUID messageId,
        UUID senderId,
        MessageType messageType,
        String text,
        Instant createdAt
) {
}
