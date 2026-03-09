package com.tinder.profiles.util;

import com.tinder.profiles.user.NewUserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Helper class for getting real JWT tokens from Keycloak in tests.
 * Includes per-user token caching and bulk parallel pre-warming.
 */
@Component
public class KeycloakTestHelper {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestHelper.class);

    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String authServiceUrl;

    // ── Token cache ──────────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private record CachedToken(String accessToken, long expiresAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs - 30_000; // 30s safety margin
        }
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public KeycloakTestHelper(String keycloakUrl, String realm, String clientId) {
        this.keycloakUrl = keycloakUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.restTemplate = new RestTemplate();
    }

    public KeycloakTestHelper() {
        this("http://localhost:9080", "spring", "spring-app");
    }

    // ── Token methods (with caching) ─────────────────────────────────────────

    /**
     * Get access token, returning a cached value if still valid.
     */
    public String getAccessToken(String username, String password) {
        CachedToken cached = tokenCache.get(username);
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken();
        }

        // Per-key atomic compute to prevent duplicate Keycloak calls
        CachedToken result = tokenCache.compute(username, (key, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            return fetchTokenFromKeycloak(username, password);
        });

        return result.accessToken();
    }

    private CachedToken fetchTokenFromKeycloak(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                clientId, username, password
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);

            if (response.getBody() != null) {
                String token = (String) response.getBody().get("access_token");
                Integer expiresIn = (Integer) response.getBody().get("expires_in");
                long expiresAtMs = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 300_000L);
                return new CachedToken(token, expiresAtMs);
            }
        } catch (Exception e) {
            log.error("Failed to get access token for {}: {}", username, e.getMessage());
            throw new RuntimeException("Could not obtain access token from Keycloak", e);
        }

        throw new RuntimeException("Keycloak returned null body for token request");
    }

    /**
     * Pre-warm tokens for all users in parallel.
     */
    public void preWarmTokens(List<NewUserRecord> users, int threadCount) {
        log.info("Pre-warming {} tokens with {} threads...", users.size(), threadCount);
        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<CompletableFuture<Void>> futures = users.stream()
                .map(user -> CompletableFuture.runAsync(() -> {
                    try {
                        getAccessToken(user.username(), user.password());
                    } catch (Exception e) {
                        log.warn("Token pre-warm failed for {}: {}", user.username(), e.getMessage());
                    }
                }, executor))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        log.info("Token pre-warming completed in {} ms, cached {} tokens",
            System.currentTimeMillis() - start, tokenCache.size());
    }

    public void clearCache() {
        tokenCache.clear();
    }

    // ── Other helper methods ─────────────────────────────────────────────────

    public String getRefreshToken(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                clientId, username, password
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            if (response.getBody() != null) {
                return (String) response.getBody().get("refresh_token");
            }
        } catch (Exception e) {
            log.error("Failed to get refresh token: {}", e.getMessage());
        }
        return null;
    }

    public Map<String, Object> getTokenResponse(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                clientId, username, password
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get token response: {}", e.getMessage());
            throw new RuntimeException("Could not obtain token from Keycloak", e);
        }
    }

    public String refreshAccessToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=refresh_token&client_id=%s&refresh_token=%s",
                clientId, refreshToken
        );

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            if (response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("Failed to refresh access token: {}", e.getMessage());
        }
        return null;
    }

    public Map<String, Object> getUserInfo(String accessToken) {
        String userInfoUrl = String.format("%s/realms/%s/protocol/openid-connect/userinfo",
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get user info: {}", e.getMessage());
            throw new RuntimeException("Could not get user info from Keycloak", e);
        }
    }

    public boolean isKeycloakAvailable() {
        try {
            String healthUrl = String.format("%s/realms/%s", keycloakUrl, realm);
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public String createAuthorizationHeader(String username, String password) {
        String token = getAccessToken(username, password);
        return "Bearer " + token;
    }

    public int getUsersCount() {
        WebClient webClient = WebClient.builder()
                .baseUrl(authServiceUrl + "/api/users/count")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        Integer count = webClient.get()
                .retrieve()
                .bodyToMono(Integer.class)
                .block();
        return count != null ? count : 0;
    }
}
