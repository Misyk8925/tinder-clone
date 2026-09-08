package com.tinder.match.security;

import com.tinder.match.conversation.model.Conversation;
import com.tinder.match.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Authorises STOMP SUBSCRIBE frames.
 * <p>
 * Authenticating the WebSocket session says who the subscriber is, but not what they may listen
 * to. Without this check any authenticated user could subscribe to
 * {@code /topic/conversations/{id}} for a conversation they are not part of and receive its
 * messages in real time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationSubscriptionInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final ConversationRepository conversationRepository;
    private final CallerProfileResolver callerProfileResolver;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            // Only conversation topics carry private data; user-scoped queues are already
            // isolated by Spring's user destination handling.
            return message;
        }

        UUID conversationId = parseConversationId(destination);
        UUID subscriberProfileId = callerProfileResolver.requireProfileId(accessor.getUser());

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AccessDeniedException("Conversation not found"));

        boolean isParticipant = subscriberProfileId.equals(conversation.getParticipant1Id())
                || subscriberProfileId.equals(conversation.getParticipant2Id());
        if (!isParticipant) {
            log.warn(
                    "Rejected conversation subscription conversationId={} profileId={}",
                    conversationId,
                    subscriberProfileId
            );
            throw new AccessDeniedException("Conversation not found");
        }

        return message;
    }

    private UUID parseConversationId(String destination) {
        String suffix = destination.substring(CONVERSATION_TOPIC_PREFIX.length());
        return Optional.of(suffix)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException e) {
                        throw new AccessDeniedException("Invalid conversation destination");
                    }
                })
                .orElseThrow(() -> new AccessDeniedException("Invalid conversation destination"));
    }
}
