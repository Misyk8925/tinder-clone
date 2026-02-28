package com.tinder.match.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                accessor.setUser(authenticate(accessor));
            }
            return message;
        }

        if ((StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))
                && accessor.getUser() == null) {
            throw new AccessDeniedException("WebSocket session is not authenticated");
        }

        return message;
    }

    private AbstractAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authorizationHeader)) {
            authorizationHeader = accessor.getFirstNativeHeader("authorization");
        }

        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AccessDeniedException("Missing or invalid Authorization header");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException("Bearer token is missing");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            if (authentication == null || !StringUtils.hasText(authentication.getName())) {
                throw new AccessDeniedException("Unable to authenticate websocket user");
            }
            return authentication;
        } catch (JwtException ex) {
            throw new AccessDeniedException("Invalid JWT token", ex);
        }
    }
}
