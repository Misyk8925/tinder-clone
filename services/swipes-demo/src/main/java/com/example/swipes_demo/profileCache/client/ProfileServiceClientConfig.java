package com.example.swipes_demo.profileCache.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ProfileServiceClientConfig {

    /**
     * Explicitly declare WebClient.Builder bean,
     * since auto-configuration may not register it in all contexts.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient profilesWebClient(
            @Value("${services.profiles.base-url}") String baseUrl,
            WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }
}
