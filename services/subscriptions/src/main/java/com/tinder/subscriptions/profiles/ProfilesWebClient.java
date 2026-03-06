package com.tinder.subscriptions.profiles;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class ProfilesWebClient {

    @Value("${profiles.base-url}")
    private String profilesBaseUrl;

    private final WebClient webClient;

    public ProfilesWebClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(profilesBaseUrl)
                .build()
        ;
    }

    public Mono<ProfileDto> getByUserId(String token){

        return webClient.get()
                .uri("/by-user")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ProfileDto.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> {
                    log.error("Error while calling profiles service", ex);
                    return Mono.empty();
                });
    }
}
