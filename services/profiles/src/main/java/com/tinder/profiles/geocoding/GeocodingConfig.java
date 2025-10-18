package com.tinder.profiles.geocoding;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration
public class GeocodingConfig {

    @Bean("nominatimWebClient")
    WebClient nominatimWebClient(
            @Value("${app.geocoding.base-url}") String baseUrl,
            @Value("${app.geocoding.user-agent}") String userAgent
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }
}
