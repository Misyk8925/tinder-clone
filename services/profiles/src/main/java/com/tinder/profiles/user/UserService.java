package com.tinder.profiles.user;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserService {
    @Getter
    private List<NewUserRecord> users;

    @Qualifier("keycloakWebClient")
    private WebClient keycloakWebClient;

    @Autowired
    public UserService(@Qualifier("keycloakWebClient") WebClient keycloakWebClient) {
        this.keycloakWebClient = keycloakWebClient;
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
     * Create test users in Keycloak with parallel HTTP calls.
     *
     * @param count number of users to generate and register
     */
    public void createTestUsers(int count) {
        setup(count);
        int existingCount = getUsersCount();
        if (existingCount < count) {
            log.info("Creating {} users in Keycloak (existing: {}) with concurrency 100...", users.size(), existingCount);
            long start = System.currentTimeMillis();

            List<String> results = Flux.fromIterable(users)
                .flatMap(user -> keycloakWebClient.post()
                    .uri("/auth/users")
                    .bodyValue(user)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(r -> log.debug("Created Keycloak user: {}", user.username()))
                    .onErrorResume(e -> {
                        log.warn("Failed to create user {}: {}", user.username(), e.getMessage());
                        return Mono.just("SKIPPED:" + user.username());
                    }),
                    100)
                .collectList()
                .block(Duration.ofMinutes(5));

            log.info("Keycloak user creation completed in {} ms ({} results)",
                    System.currentTimeMillis() - start, results != null ? results.size() : 0);
        } else {
            log.info("Keycloak already has {} users (>= {}), skipping creation", existingCount, count);
        }
    }

    /**
     * Backward-compatible overload: creates 20 users (original behavior).
     */
    public void createTestUsers() {
        createTestUsers(20);
    }

    public int getUsersCount() {
        Integer count = keycloakWebClient.get()
                .uri("/auth/users/count")
                .retrieve()
                .bodyToMono(Integer.class)
                .block();
        return count != null ? count : 0;
    }
}
