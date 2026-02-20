package com.tinder.match.conversation;

import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.dto.ConversationDto;

import java.util.UUID;

public interface ConversationService {

    ConversationDto createConversation(UUID firstParticipantId, UUID secondParticipantId);

    MessageDto sendMessage(UUID senderId, MessageDto msg);
}
