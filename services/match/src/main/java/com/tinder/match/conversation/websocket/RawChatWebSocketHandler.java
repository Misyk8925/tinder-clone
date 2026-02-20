package com.tinder.match.conversation.websocket;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.event.MessageCreatedEvent;
import com.tinder.match.conversation.model.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawChatWebSocketHandler extends TextWebSocketHandler {

    private final ConversationService conversationService;
    // Keep active raw chat sessions to broadcast newly created messages.
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ConnectionContext context = resolveContext(session);
        if (context == null) {
            session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Missing or invalid query params: senderId and conversationId are required\"}"));
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessions.add(session);
        log.info(
                "Raw chat ws connected session={} conversationId={} senderId={}",
                session.getId(),
                context.conversationId(),
                context.senderId()
        );
        session.sendMessage(new TextMessage(
                "{\"type\":\"CONNECTED\",\"endpoint\":\"ws-chat\",\"conversationId\":\"" + context.conversationId()
                        + "\",\"senderId\":\"" + context.senderId() + "\"}"
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Context is supplied once at connection time via query params.
        ConnectionContext context = resolveContext(session);
        if (context == null) {
            sendError(session, "Missing or invalid query params: senderId and conversationId are required");
            return;
        }

        String text = message.getPayload();
        if (text == null || text.trim().isEmpty()) {
            sendError(session, "Text payload is required");
            return;
        }

        try {
            // Raw endpoint accepts plain text, then maps it to existing application DTO/service flow.
            MessageDto request = new MessageDto(
                    context.conversationId(),
                    UUID.randomUUID(),
                    MessageType.TEXT,
                    text,
                    List.of()
            );
            MessageDto saved = conversationService.sendMessage(context.senderId(), request);
            // Ack confirms server accepted and persisted the message request.
            session.sendMessage(new TextMessage(
                    "{\"type\":\"ACK\",\"conversationId\":\"" + saved.conversationId()
                            + "\",\"clientMessageId\":\"" + saved.clientMessageId()
                            + "\",\"messageType\":\"" + saved.messageType() + "\"}"
            ));
        } catch (Exception ex) {
            log.warn("Raw chat ws send failed for session {}", session.getId(), ex);
            sendError(session, ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Raw chat ws disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcast(MessageCreatedEvent event) {
        // Broadcast event only to sessions that belong to the same conversation.
        TextMessage outbound = new TextMessage(serialize(event));
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            ConnectionContext context = resolveContext(session);
            if (context == null || !event.conversationId().equals(context.conversationId())) {
                continue;
            }
            try {
                session.sendMessage(outbound);
            } catch (IOException e) {
                sessions.remove(session);
                log.warn("Failed to broadcast raw chat ws message to session {}", session.getId(), e);
            }
        }
    }

    private ConnectionContext resolveContext(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        // Support both camelCase and hyphenated query keys.
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String senderRaw = first(params, "senderId", "sender-id");
        String conversationRaw = first(params, "conversationId", "conversation-id");
        if (senderRaw == null || conversationRaw == null) {
            return null;
        }

        try {
            UUID senderId = UUID.fromString(senderRaw);
            UUID conversationId = UUID.fromString(conversationRaw);
            return new ConnectionContext(senderId, conversationId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String first(MultiValueMap<String, String> params, String firstKey, String secondKey) {
        String first = params.getFirst(firstKey);
        if (first != null && !first.isBlank()) {
            return first;
        }
        String second = params.getFirst(secondKey);
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private void sendError(WebSocketSession session, String error) throws IOException {
        session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"" + escapeJson(error) + "\"}"));
    }

    private String serialize(MessageCreatedEvent event) {
        String safeText = event.text() == null ? null : escapeJson(event.text());
        return "{"
                + "\"eventId\":\"" + event.eventId() + "\","
                + "\"occurredAt\":\"" + event.occurredAt() + "\","
                + "\"messageId\":\"" + event.messageId() + "\","
                + "\"conversationId\":\"" + event.conversationId() + "\","
                + "\"senderId\":\"" + event.senderId() + "\","
                + "\"clientMessageId\":\"" + event.clientMessageId() + "\","
                + "\"conversationSeq\":" + event.conversationSeq() + ","
                + "\"type\":\"" + event.type() + "\","
                + "\"text\":" + (safeText == null ? "null" : "\"" + safeText + "\"")
                + "}";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ConnectionContext(UUID senderId, UUID conversationId) {
    }
}
