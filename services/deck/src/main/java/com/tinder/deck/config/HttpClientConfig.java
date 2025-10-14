package com.tinder.deck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean
    WebClient profilesWebClient(@Value("${profiles.base-url}") String profilesUrl) {
        return WebClient.builder()
                .baseUrl(profilesUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().compress(true)
                ))
                .build();
    }

    @Bean
    WebClient swipesWebClient(@Value("${swipes.base-url}") String swipesUrl) {
        return WebClient.builder()
                .baseUrl(swipesUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().compress(true)
                ))
                .build();
    }
}
