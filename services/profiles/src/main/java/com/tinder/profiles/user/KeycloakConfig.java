package com.tinder.profiles.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KeycloakConfig {
    @Bean("keycloakWebClient")
    public WebClient keycloakWebClient(
            @Value("${keycloak.auth-server-url}") String keycloakUrl
    ) {
        return WebClient.builder()
                .baseUrl(keycloakUrl)
                .build();
    }
}
