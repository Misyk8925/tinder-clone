package com.tinder.match.conversation.dto;

import com.tinder.match.conversation.model.ConversationStatus;

import java.util.List;
import java.util.UUID;

public record ConversationWithMessagesDto(
        UUID conversationId,
        UUID participant1Id,
        UUID participant2Id,
        ConversationStatus status,
        List<MessageHistoryDto> messages
) {
}
