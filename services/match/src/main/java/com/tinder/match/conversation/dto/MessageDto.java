package com.tinder.match.conversation.dto;

import com.tinder.match.conversation.model.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MessageDto(
        @NotNull UUID conversationId,
        @NotNull UUID clientMessageId,
        @NotNull MessageType messageType,
        @Size(max = 5000) String text,
        @Valid List<@Valid MessageAttachmentDto> attachments
) {
    @AssertTrue(message = "TEXT requires non-blank text and no attachments")
    public boolean isTextMessageValid() {
        if (messageType != MessageType.TEXT) return true;
        return text != null && !text.trim().isEmpty()
                && (attachments == null || attachments.isEmpty());
    }

    @AssertTrue(message = "IMAGE/VIDEO/FILE requires at least one attachment")
    public boolean isMediaMessageValid() {
        if (messageType == MessageType.TEXT) return true;
        return attachments != null && !attachments.isEmpty();
    }
}
