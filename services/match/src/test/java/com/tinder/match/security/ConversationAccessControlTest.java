package com.tinder.match.security;

import com.tinder.match.conversation.ConversationNotAccessibleException;
import com.tinder.match.conversation.implementations.ConversationPhotoStorageService;
import com.tinder.match.conversation.implementations.ConversationServiceImpl;
import com.tinder.match.conversation.model.Conversation;
import com.tinder.match.conversation.repository.ConversationRepository;
import com.tinder.match.conversation.repository.MessageRepository;
import com.tinder.match.moderation.ModerationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Conversations are private to their two participants. These tests pin that down at both
 * entry points: the REST read and the STOMP subscription.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationAccessControlTest {

    private static final UUID PARTICIPANT_ONE = UUID.randomUUID();
    private static final UUID PARTICIPANT_TWO = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationPhotoStorageService photoStorageService;
    @Mock
    private ModerationClient moderationClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CallerProfileResolver callerProfileResolver;

    private ConversationServiceImpl conversationService;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                eventPublisher, conversationRepository, messageRepository, photoStorageService, moderationClient);
        conversation = Conversation.createActive(PARTICIPANT_ONE, PARTICIPANT_TWO);
        when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(photoStorageService.downloadUrls(any(), any())).thenReturn(java.util.Map.of());
    }

    @Test
    @DisplayName("a participant can read the conversation")
    void participantCanReadConversation() {
        var conversationId = UUID.randomUUID();

        var loaded = conversationService.getConversation(conversationId, PARTICIPANT_ONE);

        assertThat(loaded.participant1Id()).isEqualTo(conversation.getParticipant1Id());
    }

    @Test
    @DisplayName("a non-participant cannot read the conversation, and is not told it exists")
    void outsiderCannotReadConversation() {
        var conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> conversationService.getConversation(conversationId, OUTSIDER))
                .isInstanceOf(ConversationNotAccessibleException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    @DisplayName("an unauthenticated read is refused")
    void nullCallerCannotReadConversation() {
        assertThatThrownBy(() -> conversationService.getConversation(UUID.randomUUID(), null))
                .isInstanceOf(ConversationNotAccessibleException.class);
    }

    @Test
    @DisplayName("a malformed message is a bad request, not a missing conversation")
    void invalidPayloadIsNotReportedAsNotFound() {
        var message = new com.tinder.match.conversation.dto.MessageDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                com.tinder.match.conversation.model.MessageType.TEXT,
                "   ",
                List.of());

        // Blank TEXT is a validation failure. It must stay a plain MessagingException so the
        // advice maps it to 400 — reporting it as ConversationNotAccessibleException would tell
        // a participant their own conversation had vanished.
        assertThatThrownBy(() -> conversationService.sendMessage(PARTICIPANT_ONE, message))
                .isInstanceOf(MessagingException.class)
                .isNotInstanceOf(ConversationNotAccessibleException.class);
    }

    @Test
    @DisplayName("a non-participant cannot subscribe to the conversation topic")
    void outsiderCannotSubscribeToConversationTopic() {
        var conversationId = UUID.randomUUID();
        var interceptor = new ConversationSubscriptionInterceptor(conversationRepository, callerProfileResolver);
        when(callerProfileResolver.requireProfileId(any(java.security.Principal.class))).thenReturn(OUTSIDER);

        assertThatThrownBy(() -> interceptor.preSend(subscribeTo("/topic/conversations/" + conversationId), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a participant may subscribe to the conversation topic")
    void participantMaySubscribeToConversationTopic() {
        var conversationId = UUID.randomUUID();
        var interceptor = new ConversationSubscriptionInterceptor(conversationRepository, callerProfileResolver);
        when(callerProfileResolver.requireProfileId(any(java.security.Principal.class))).thenReturn(PARTICIPANT_TWO);

        var message = subscribeTo("/topic/conversations/" + conversationId);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    @DisplayName("a malformed conversation destination is refused rather than passed through")
    void malformedDestinationIsRefused() {
        var interceptor = new ConversationSubscriptionInterceptor(conversationRepository, callerProfileResolver);
        when(callerProfileResolver.requireProfileId(any(java.security.Principal.class))).thenReturn(PARTICIPANT_ONE);

        assertThatThrownBy(() -> interceptor.preSend(subscribeTo("/topic/conversations/not-a-uuid"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private org.springframework.messaging.Message<byte[]> subscribeTo(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(() -> "user");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
