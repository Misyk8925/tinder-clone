package com.tinder.match.conversation.dto;

import com.tinder.match.conversation.model.ConversationStatus;

import java.util.UUID;

public record ConversationDto(
        UUID conversationId,
        UUID participant1Id,
        UUID participant2Id,
        ConversationStatus status
) {
}
