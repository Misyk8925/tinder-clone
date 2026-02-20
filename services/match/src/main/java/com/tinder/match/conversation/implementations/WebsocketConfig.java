package com.tinder.match.conversation.implementations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/queue", "/topic"); // private rooms
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompLoggingInterceptor("INBOUND"));
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompLoggingInterceptor("OUTBOUND"));
    }

    private ChannelInterceptor stompLoggingInterceptor(String direction) {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();
                if (command == null) {
                    return message;
                }

                String sessionId = accessor.getSessionId();
                String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
                String destination = accessor.getDestination();
                String subscriptionId = accessor.getSubscriptionId();
                int payloadBytes = payloadSize(message.getPayload());

                if (command == StompCommand.CONNECT
                        || command == StompCommand.CONNECTED
                        || command == StompCommand.DISCONNECT
                        || command == StompCommand.SEND
                        || command == StompCommand.SUBSCRIBE
                        || command == StompCommand.UNSUBSCRIBE
                        || command == StompCommand.ERROR) {
                    log.info(
                            "STOMP {} cmd={} session={} user={} destination={} subscription={} payloadBytes={}",
                            direction,
                            command,
                            sessionId,
                            user,
                            destination,
                            subscriptionId,
                            payloadBytes
                    );
                } else {
                    log.debug(
                            "STOMP {} cmd={} session={} destination={} payloadBytes={}",
                            direction,
                            command,
                            sessionId,
                            destination,
                            payloadBytes
                    );
                }
                return message;
            }
        };
    }

    private int payloadSize(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes.length;
        }
        if (payload instanceof String text) {
            return text.length();
        }
        return -1;
    }
}
