package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.model.ConversationStatus;
import com.tinder.match.security.AuthenticatedProfileResolver;
import com.tinder.match.security.UserProfileMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationRestControllerSecurityTest {

    @Test
    void givenANonParticipantWhenReadingAConversationThenAccessIsDenied() {
        ConversationService conversations = mock(ConversationService.class);
        AuthenticatedProfileResolver profiles = mock(AuthenticatedProfileResolver.class);
        Jwt jwt = mock(Jwt.class);
        UUID conversationId = UUID.randomUUID();
        when(profiles.resolve(jwt)).thenReturn(UUID.randomUUID());
        when(conversations.getConversation(conversationId)).thenReturn(new ConversationWithMessagesDto(
                conversationId, UUID.randomUUID(), UUID.randomUUID(), ConversationStatus.ACTIVE, List.of()));

        ConversationRestController controller = new ConversationRestController(
                conversations, new UserProfileMappingService(), profiles);

        assertThatThrownBy(() -> controller.getConversation(conversationId, jwt))
                .isInstanceOf(AccessDeniedException.class);
    }
}
