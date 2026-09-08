package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.moderation.ContentBlockedException;
import com.tinder.match.security.CallerProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ConversationWsController {

    private final ConversationService conversationService;
    private final CallerProfileResolver callerProfileResolver;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(
            @Valid @Payload MessageDto message,
            Principal principal,
            @Header(name = "simpSessionId", required = false) String sessionId
    ) {
        // The sender is whoever the STOMP session's JWT says it is. Nothing in the payload
        // can influence it, so a client cannot post messages as another profile.
        UUID senderId = callerProfileResolver.requireProfileId(principal);
        log.info(
                "STOMP send session={} senderId={} conversationId={} clientMessageId={} type={}",
                sessionId,
                senderId,
                message.conversationId(),
                message.clientMessageId(),
                message.messageType()
        );
        try {
            conversationService.sendMessage(senderId, message);
        } catch (ContentBlockedException blocked) {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("type", "MODERATION_BLOCKED");
            payload.put("clientMessageId", String.valueOf(message.clientMessageId()));
            payload.put("text", blocked.getMessage());
            messagingTemplate.convertAndSend("/topic/conversations/" + message.conversationId(), (Object) payload);
        }
    }
}
