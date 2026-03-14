package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.security.UserProfileMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ConversationWsController {

    private final ConversationService conversationService;
    private final UserProfileMappingService userProfileMappingService;

    @MessageMapping("/chat.send")
    public void send(
            @Valid @Payload MessageDto message,
            Principal principal,
            @Header(name = "simpSessionId", required = false) String sessionId
    ) {
        UUID senderId = resolveSenderId(principal);
        log.info(
                "STOMP send session={} senderId={} conversationId={} clientMessageId={} type={}",
                sessionId,
                senderId,
                message.conversationId(),
                message.clientMessageId(),
                message.messageType()
        );
        conversationService.sendMessage(senderId, message);
    }

    private UUID resolveSenderId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new MessagingException("Authenticated sender is required");
        }

        String userId = principal.getName();

        // Primary: look up the profile UUID registered when the user called the REST layer.
        // Conversations store participant IDs as profile UUIDs, while the JWT sub is the
        // Keycloak user ID — a different UUID.  UserProfileMappingService bridges the two.
        UUID profileId = userProfileMappingService.resolve(userId);
        if (profileId != null) {
            return profileId;
        }

        // Fallback: the JWT sub might already be a profile UUID (e.g. in test scenarios
        // where Keycloak user IDs happen to equal profile IDs).
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ignored) {
            throw new MessagingException(
                    "Cannot resolve sender profile ID for user: " + userId
                    + ". Call GET /rest/conversations/{id}?callerProfileId=<profileId> before connecting STOMP."
            );
        }
    }
}
