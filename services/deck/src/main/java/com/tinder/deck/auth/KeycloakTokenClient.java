package com.tinder.deck.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive Keycloak token client using client_credentials grant
 * Caches token in-memory until near expiry and refreshes on demand
 */
@Component
public class KeycloakTokenClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenClient.class);

    private final WebClient http;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final long skewSeconds;

    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public KeycloakTokenClient(
            @Value("${keycloak.token-uri:}") String tokenUri,
            @Value("${keycloak.client-id:}") String clientId,
            @Value("${keycloak.client-secret:}") String clientSecret,
            @Value("${keycloak.scope:}") String scope,
            @Value("${keycloak.clock-skew-seconds:30}") long skewSeconds
    ) {
        this.http = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.skewSeconds = skewSeconds;
    }

    public boolean isConfigured() {
        return tokenUri != null && !tokenUri.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    // Get current valid access token (reactive, non-blocking)
    public Mono<String> getAccessToken() {
        if (!isConfigured()) {
            return Mono.empty();
        }
        CachedToken ct = cache.get();
        if (ct != null && !ct.isExpired(skewSeconds)) {
            return Mono.just(ct.accessToken);
        }
        return requestNewToken()
                .doOnNext(token -> cache.set(new CachedToken(token.value, token.expiresAt)))
                .map(Token::value);
    }

    private Mono<Token> requestNewToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        return http.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(tr -> {
                    long expiresIn = tr.getExpires_in() != null ? tr.getExpires_in() : 300L;
                    Instant expiresAt = Instant.now().plusSeconds(expiresIn);
                    log.debug("Obtained Keycloak access token, expires in {}s", expiresIn);
                    return new Token(tr.getAccess_token(), expiresAt);
                })
                .doOnError(e -> log.warn("Failed to obtain Keycloak token: {}", e.toString()));
    }

    private record Token(String value, Instant expiresAt) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TokenResponse {
        private String access_token;
        private String token_type;
        private Long expires_in;
        private Long refresh_expires_in;
        private String scope;
        private String session_state;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired(long skewSec) {
            return Instant.now().isAfter(expiresAt.minusSeconds(skewSec));
        }
    }
}

