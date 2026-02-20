package com.tinder.match.conversation.implementations;

import com.tinder.match.conversation.websocket.RawChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GlobalWebSocketConfig implements WebSocketConfigurer {

    private final RawChatWebSocketHandler rawChatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rawChatWebSocketHandler, "/ws-chat")
                .setAllowedOrigins("*");
    }
}
