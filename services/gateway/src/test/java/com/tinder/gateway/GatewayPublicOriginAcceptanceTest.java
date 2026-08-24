package com.tinder.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("acceptance")
@DisplayName("Feature: Gateway accepts the Lunari production origin")
class GatewayPublicOriginAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given the production frontend, when CORS is evaluated, then lunari.misyk.tech is allowed")
    void allowsTheLunariProductionOrigin() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/profiles/me").build());

        CorsConfiguration configuration = new SecurityConfig()
                .corsConfigurationSource()
                .getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:4200", "https://lunari.misyk.tech")
                .doesNotContain("https://matchapp.misyk.tech");
    }
}
