package com.tinder.match.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PhotosClientConfig {

    @Bean
    RestClient photosRestClient(PhotosServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getService().getUrl())
                .requestFactory(factory);

        String internalAuthSecret = properties.getService().getInternalAuthSecret();
        if (internalAuthSecret != null && !internalAuthSecret.isBlank()) {
            builder.defaultHeader("X-Internal-Auth", internalAuthSecret);
        }
        return builder.build();
    }
}
