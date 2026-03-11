package com.tinder.profiles.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KeycloakConfig {

    /**
     * WebClient pointing directly at the self-hosted Keycloak instance.
     * Used by both {@link KeycloakAdminClient} and {@link UserService}
     * for all Admin REST API calls.
     */
    @Bean("selfHostedKeycloakWebClient")
    public WebClient selfHostedKeycloakWebClient(
            @Value("${keycloak.keycloakUrl}") String keycloakUrl
    ) {
        return WebClient.builder()
                .baseUrl(keycloakUrl)
                .build();
    }
}
