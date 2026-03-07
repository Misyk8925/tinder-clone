package com.tinder.profiles.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class KeycloakAdminClient {

    private final WebClient keycloakWebClient;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakAdminClient(
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

    /**
     * Assigns a realm role to the given Keycloak user.
     * The role-mappings endpoint is idempotent — safe to call multiple times.
     *
     * @param userId   Keycloak user ID (same as JWT subject)
     * @param roleName Keycloak realm role name (e.g. "USER_PREMIUM")
     */
    public void assignRealmRole(String userId, String roleName) {
        log.info("Assigning Keycloak role '{}' to user '{}'", roleName, userId);

        String token = fetchAdminToken();
        KeycloakRoleRepresentation role = fetchRoleRepresentation(token, roleName);
        postRoleMapping(token, userId, role);

        log.info("Keycloak role '{}' successfully assigned to user '{}'", roleName, userId);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        // Admin REST API requires a token from the master realm, not the target realm.
        // admin-cli is the built-in public client in master intended for this.
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
                .block();

        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("Keycloak token response missing access_token");
        }
        return (String) body.get("access_token");
    }

    private KeycloakRoleRepresentation fetchRoleRepresentation(String token, String roleName) {
        KeycloakRoleRepresentation role = keycloakWebClient.get()
                .uri("/admin/realms/{realm}/roles/{roleName}", realm, roleName)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(KeycloakRoleRepresentation.class)
                .block();

        if (role == null || role.id() == null) {
            throw new IllegalStateException("Role '" + roleName + "' not found in realm '" + realm + "'");
        }
        return role;
    }

    private void postRoleMapping(String token, String userId, KeycloakRoleRepresentation role) {
        keycloakWebClient.post()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(role))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
