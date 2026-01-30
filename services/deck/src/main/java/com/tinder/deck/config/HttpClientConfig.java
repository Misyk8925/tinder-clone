package com.tinder.deck.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean
    WebClient profilesWebClient(@Value("${profiles.test-url}") String profilesUrl) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofMillis(3_000))
                .compress(true);

        return WebClient.builder()
                .baseUrl(profilesUrl)
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }

    @Bean
    WebClient swipesWebClient(@Value("${swipes.base-url}") String swipesUrl) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofMillis(3_000))
                .compress(true);

        return WebClient.builder()
                .baseUrl(swipesUrl)
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }
}
