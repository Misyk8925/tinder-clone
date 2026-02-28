package com.tinder.match.conversation;

import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.dto.ConversationDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ConversationService {

    ConversationDto createConversation(UUID firstParticipantId, UUID secondParticipantId);

    MessageDto sendMessage(UUID senderId, MessageDto msg);

    MessageDto sendPhotoMessage(UUID senderId, UUID conversationId, UUID clientMessageId, MultipartFile file);
}
