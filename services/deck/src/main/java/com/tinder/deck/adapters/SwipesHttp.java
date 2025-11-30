package com.tinder.deck.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwipesHttp {

    private final WebClient swipesWebClient;
    private static String token;

    static{
        refreshToken();
    }

    @Scheduled(cron = "0 */1 * * * *")
    public static String refreshToken() {

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:9080")
                .build();

        // Send form-encoded request and parse response as Map
        java.util.Map<?, ?> response = webClient.post()
                .uri("/realms/spring/protocol/openid-connect/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("client_id=spring-app&grant_type=password&username=kovalmisha2000@gmail.com&password=koval")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .block();

        SwipesHttp.token = (response != null) ? (String) response.get("access_token") : null;
        return SwipesHttp.token;

    }

    public Mono<Map<UUID, Boolean>> betweenBatch(UUID viewerId, List<UUID> candidateIds) {
        return swipesWebClient.post()
                .uri("/between/batch?viewerId={id}", viewerId)
                .header("Authorization", "Bearer " + SwipesHttp.token)
                .bodyValue(candidateIds)
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<UUID, Boolean>>() {});
    }
}
