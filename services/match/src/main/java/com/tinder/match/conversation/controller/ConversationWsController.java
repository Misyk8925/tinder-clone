package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.MessageDto;
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

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ignored) {
            throw new MessagingException("Authenticated principal name is not a valid UUID sender id");
        }
    }
}
