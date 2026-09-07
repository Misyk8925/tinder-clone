package com.tinder.match.conversation.implementations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The chat socket must never be opened to every origin. A misconfigured wildcard should stop
 * the service at startup rather than quietly accept any site's handshake.
 */
class WebsocketOriginConfigTest {

    @Test
    @DisplayName("a wildcard origin is rejected at startup")
    void wildcardOriginIsRejected() {
        WebsocketConfig config = new WebsocketConfig(null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("*"));

        assertThatThrownBy(() -> config.registerStompEndpoints(mock(StompEndpointRegistry.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact origins");
    }

    @Test
    @DisplayName("a subdomain wildcard pattern is rejected too")
    void wildcardPatternIsRejected() {
        WebsocketConfig config = new WebsocketConfig(null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("https://*.example.com"));

        assertThatThrownBy(() -> config.registerStompEndpoints(mock(StompEndpointRegistry.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact origins");
    }

    @Test
    @DisplayName("an empty allowlist is rejected rather than defaulting to open")
    void emptyOriginListIsRejected() {
        WebsocketConfig config = new WebsocketConfig(null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of());

        assertThatThrownBy(() -> config.registerStompEndpoints(mock(StompEndpointRegistry.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one origin");
    }
}
