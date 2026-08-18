package com.tinder.profiles.infrastructure.external.keycloak;

import com.tinder.profiles.application.profile.port.out.PremiumRolePort;
import com.tinder.profiles.config.props.KeycloakProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class KeycloakPremiumRoleAdapter implements PremiumRolePort {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    private final WebClient keycloakWebClient;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakPremiumRoleAdapter(
            @Qualifier("selfHostedKeycloakWebClient") WebClient keycloakWebClient,
            KeycloakProperties properties
    ) {
        this.keycloakWebClient = keycloakWebClient;
        this.realm = properties.realm();
        this.adminUsername = properties.adminUsername();
        this.adminPassword = properties.adminPassword();
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

    /**
     * Removes a realm role from the given Keycloak user.
     * Safe to call even if the user does not have the role assigned.
     *
     * @param userId   Keycloak user ID (same as JWT subject)
     * @param roleName Keycloak realm role name (e.g. "USER_PREMIUM")
     */
    public void removeRealmRole(String userId, String roleName) {
        log.info("Removing Keycloak role '{}' from user '{}'", roleName, userId);

        String token = fetchAdminToken();
        KeycloakRoleRepresentation role = fetchRoleRepresentation(token, roleName);
        deleteRoleMapping(token, userId, role);

        log.info("Keycloak role '{}' successfully removed from user '{}'", roleName, userId);
    }

    @Override
    public void grantPremium(String userId) {
        assignRealmRole(userId, PREMIUM_ROLE);
    }

    @Override
    public void revokePremium(String userId) {
        removeRealmRole(userId, PREMIUM_ROLE);
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

    private void deleteRoleMapping(String token, String userId, KeycloakRoleRepresentation role) {
        keycloakWebClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(role))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
