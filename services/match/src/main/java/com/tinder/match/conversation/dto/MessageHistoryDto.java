package com.tinder.match.conversation.dto;

import com.tinder.match.conversation.model.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageHistoryDto(
        UUID messageId,
        UUID senderId,
        MessageType messageType,
        String text,
        List<MessageAttachmentDto> attachments,
        Instant createdAt
) {
}
