package com.tinder.match.conversation.implementations;

import com.tinder.match.security.ConversationSubscriptionInterceptor;
import com.tinder.match.security.WebSocketJwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor webSocketJwtChannelInterceptor;
    private final ConversationSubscriptionInterceptor conversationSubscriptionInterceptor;

    /**
     * Origins allowed to open the WebSocket handshake. A wildcard here lets any website open an
     * authenticated socket against this service on a visitor's behalf, so the list is explicit
     * and configured per environment.
     */
    @Value("${app.websocket.allowed-origins:http://localhost:4200,https://lunari.misyk.tech}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/queue", "/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authenticate first, then authorise what the authenticated session may subscribe to.
        registration.interceptors(webSocketJwtChannelInterceptor, conversationSubscriptionInterceptor);
    }
}
