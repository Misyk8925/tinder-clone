package com.tinder.deck.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean
    @ConditionalOnBean(ReactiveClientRegistrationRepository.class)
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        
        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
                ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultReactiveOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository);
        
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        
        return authorizedClientManager;
    }

    @Bean
    WebClient profilesWebClient(
            @Value("${profiles.base-url}") String profilesUrl,
            @Autowired(required = false) ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofMillis(3_000))
                .compress(true);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(profilesUrl)
                .clientConnector(new ReactorClientHttpConnector(client));

        // Only add OAuth2 filter if authorizedClientManager is available
        if (authorizedClientManager != null) {
            ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                    new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
            oauth2.setDefaultClientRegistrationId("keycloak-client");
            builder.filter(oauth2);
        }

        return builder.build();
    }

    @Bean
    WebClient swipesWebClient(
            @Value("${swipes.base-url}") String swipesUrl,
            @Autowired(required = false) ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofMillis(3_000))
                .compress(true);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(swipesUrl)
                .clientConnector(new ReactorClientHttpConnector(client));

        // Only add OAuth2 filter if authorizedClientManager is available
        if (authorizedClientManager != null) {
            ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                    new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
            oauth2.setDefaultClientRegistrationId("keycloak-client");
            builder.filter(oauth2);
        }

        return builder.build();
    }
}
