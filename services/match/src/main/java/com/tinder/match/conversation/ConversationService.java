package com.tinder.match.conversation;

import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.dto.ConversationDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ConversationService {

    ConversationDto createConversation(UUID firstParticipantId, UUID secondParticipantId);

    /**
     * Loads a conversation and its messages for {@code callerProfileId}, which must be one of
     * the two participants. Callers never pass an identity of their own choosing — it comes
     * from the authenticated principal.
     */
    ConversationWithMessagesDto getConversation(UUID conversationId, UUID callerProfileId);

    MessageDto sendMessage(UUID senderId, MessageDto msg);

    MessageDto sendPhotoMessage(UUID senderId, UUID conversationId, UUID clientMessageId, MultipartFile file);

    List<ConversationDto> getMyChats(UUID userId);
}
