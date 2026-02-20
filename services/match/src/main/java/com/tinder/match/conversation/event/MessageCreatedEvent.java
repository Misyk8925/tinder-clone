package com.tinder.match.conversation.event;

import com.tinder.match.conversation.model.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        UUID clientMessageId,
        long conversationSeq,
        MessageType type,
        String text
) {
}
