package com.tinder.profiles.user;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserService {

    @Getter
    private List<NewUserRecord> users;

    private final WebClient keycloakWebClient;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public UserService(
            @Qualifier("selfHostedKeycloakWebClient") WebClient keycloakWebClient,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.admin-username}") String adminUsername,
            @Value("${keycloak.admin-password}") String adminPassword
    ) {
        this.keycloakWebClient = keycloakWebClient;
        this.realm = realm;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    private static final String[] FIRST_NAME_POOL = {
        "Alexander", "Maria", "Dmitry", "Anna", "Ivan",
        "Catherine", "Sergey", "Olga", "Andrew", "Natalie",
        "Michael", "Elena", "Alex", "Tatiana", "Vladimir",
        "Irina", "Nicholas", "Svetlana", "Paul", "Julia"
    };

    private static final String[] LAST_NAME_POOL = {
        "Ivanov", "Petrova", "Sidorov", "Kozlova", "Smirnov",
        "Novikova", "Popov", "Morozova", "Vasiliev", "Volkova",
        "Sokolov", "Zaitseva", "Lebedev", "Semenova", "Egorov",
        "Pavlova", "Kozlov", "Golubeva", "Stepanov", "Vinogradova"
    };

    private void setup(int count) {
        this.users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String username = "user" + (i + 1) + "@test.com";
            String password = "Password" + (i + 1) + "!";
            int cycle = i / FIRST_NAME_POOL.length;
            String firstName = FIRST_NAME_POOL[i % FIRST_NAME_POOL.length] + (cycle > 0 ? cycle : "");
            String lastName = LAST_NAME_POOL[i % LAST_NAME_POOL.length];
            users.add(new NewUserRecord(username, password, firstName, lastName));
        }
        log.info("Generated {} test users", users.size());
    }

    /**
     * Fetch an admin token from the Keycloak master realm.
     * Uses the built-in admin-cli client (direct grant) to obtain a bearer token
     * that is then passed to every Admin REST API call.
     */
    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        Map<String, Object> body = keycloakWebClient.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("Keycloak admin token response is missing access_token");
        }
        return (String) body.get("access_token");
    }

    /**
     * Build the Keycloak UserRepresentation payload required by the Admin REST API.
     * Passwords are set via the 'credentials' field with a non-temporary password.
     */
    private Map<String, Object> toKeycloakUserRepresentation(NewUserRecord user) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", user.password(),
                "temporary", false
        );
        return Map.of(
                "username", user.username(),
                "email", user.username(),
                "firstName", user.firstName(),
                "lastName", user.lastName(),
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential)
        );
    }

    /**
     * Create test users in Keycloak directly via Admin REST API with parallel HTTP calls.
     *
     * @param count number of users to generate and register
     */
    public void createTestUsers(int count) {
        setup(count);
        String token = fetchAdminToken();
        int existingCount = getUsersCount(token);

        if (existingCount < count) {
            log.info("Creating {} users in Keycloak realm '{}' (existing: {}) with concurrency 100...",
                    users.size(), realm, existingCount);
            long start = System.currentTimeMillis();

            List<String> results = Flux.fromIterable(users)
                .flatMap(user -> keycloakWebClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toKeycloakUserRepresentation(user))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnSuccess(r -> log.debug("Created Keycloak user: {}", user.username()))
                    .onErrorResume(e -> {
                        log.warn("Failed to create user {}: {}", user.username(), e.getMessage());
                        return Mono.empty();
                    })
                    .thenReturn("OK:" + user.username()),
                    100)
                .collectList()
                .block(Duration.ofMinutes(5));

            log.info("Keycloak user creation completed in {} ms ({} results)",
                    System.currentTimeMillis() - start, results != null ? results.size() : 0);
        } else {
            log.info("Keycloak realm '{}' already has {} users (>= {}), skipping creation",
                    realm, existingCount, count);
        }
    }

    /**
     * Backward-compatible overload: creates 20 users (original behavior).
     */
    public void createTestUsers() {
        createTestUsers(20);
    }

    public int getUsersCount() {
        return getUsersCount(fetchAdminToken());
    }

    /**
     * Fetch total user count from Keycloak Admin REST API using a pre-fetched token.
     */
    private int getUsersCount(String token) {
        Integer count = keycloakWebClient.get()
                .uri("/admin/realms/{realm}/users/count", realm)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Integer.class)
                .block(Duration.ofSeconds(10));
        return count != null ? count : 0;
    }
}
