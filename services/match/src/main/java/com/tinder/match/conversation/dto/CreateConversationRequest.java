package com.tinder.match.conversation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull UUID participant1Id,
        @NotNull UUID participant2Id
) {
}
