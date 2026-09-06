package com.tinder.profiles.config.moderation;

import com.tinder.profiles.config.props.ModerationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class ModerationClientConfig {

    @Bean("moderationWebClient")
    WebClient moderationWebClient(ModerationProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(properties.service().timeout());
        return WebClient.builder()
                .baseUrl(properties.service().url())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
