package com.tinder.match.conversation.websocket;

import com.tinder.match.conversation.event.MessageCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class GlobalMessagesWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("Global ws connected: {}", session.getId());
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Global ws disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcast(MessageCreatedEvent event) {
        TextMessage message = new TextMessage(serialize(event));
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                sessions.remove(session);
                log.warn("Failed to send global ws message to session {}", session.getId(), e);
            }
        }
    }

    public int connectedCount() {
        cleanupClosedSessions();
        return sessions.size();
    }

    public boolean hasConnections() {
        return connectedCount() > 0;
    }

    private void cleanupClosedSessions() {
        sessions.removeIf(session -> !session.isOpen());
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
}
