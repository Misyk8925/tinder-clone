package com.tinder.profiles.config.keycloak;

import com.tinder.profiles.config.props.KeycloakProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KeycloakConfig {

    /**
     * WebClient pointing directly at the self-hosted Keycloak instance.
     * Used by every Keycloak Admin REST API caller in the service.
     */
    @Bean("selfHostedKeycloakWebClient")
    public WebClient selfHostedKeycloakWebClient(KeycloakProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.keycloakUrl())
                .build();
    }
}
