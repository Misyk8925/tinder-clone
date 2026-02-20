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
            @Header(name = "sender-id", required = false) UUID senderIdHeader,
            @Header(name = "simpSessionId", required = false) String sessionId
    ) {
        log.info(
                "WS SEND received session={} principal={} senderHeader={} conversationId={} clientMessageId={} type={}",
                sessionId,
                principalName(principal),
                senderIdHeader,
                message.conversationId(),
                message.clientMessageId(),
                message.messageType()
        );
        UUID senderId = resolveSenderId(principal, senderIdHeader);
        log.info(
                "WS SEND resolved sender session={} senderId={} conversationId={} clientMessageId={}",
                sessionId,
                senderId,
                message.conversationId(),
                message.clientMessageId()
        );
        conversationService.sendMessage(senderId, message);
        log.info(
                "WS SEND completed session={} senderId={} conversationId={} clientMessageId={}",
                sessionId,
                senderId,
                message.conversationId(),
                message.clientMessageId()
        );
    }

    private UUID resolveSenderId(Principal principal, UUID senderIdHeader) {

        if (principal != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (IllegalArgumentException ignored) {
                if (senderIdHeader != null) {
                    return senderIdHeader;
                }
                throw new MessagingException("Principal name is not a valid UUID sender id");
            }
        }

        if (senderIdHeader != null) {
            return senderIdHeader;
        }

        throw new MessagingException("Sender id is required");
    }

    private String principalName(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }
}
