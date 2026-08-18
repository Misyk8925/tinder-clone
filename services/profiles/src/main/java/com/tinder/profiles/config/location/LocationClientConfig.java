package com.tinder.profiles.config.location;

import com.tinder.profiles.config.props.LocationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LocationClientConfig {

    @Bean("locationWebClient")
    WebClient locationWebClient(LocationProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.service().url())
                .build();
    }
}
