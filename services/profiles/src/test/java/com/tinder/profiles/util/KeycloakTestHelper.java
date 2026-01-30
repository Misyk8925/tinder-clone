package com.tinder.profiles.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Helper class for getting real JWT tokens from Keycloak in tests
 */
@Component
public class KeycloakTestHelper {

    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final RestTemplate restTemplate;

    public KeycloakTestHelper(String keycloakUrl, String realm, String clientId) {
        this.keycloakUrl = keycloakUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Default constructor with common values
     */
    public KeycloakTestHelper() {
        this("http://localhost:9080", "spring", "spring-app");
    }

    /**
     * Get access token using username and password
     */
    public String getAccessToken(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                clientId, username, password
        );

        org.springframework.http.HttpEntity<String> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            
            if (response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            System.err.println("Failed to get access token: " + e.getMessage());
            throw new RuntimeException("Could not obtain access token from Keycloak", e);
        }
        
        return null;
    }

    /**
     * Get refresh token
     */
    public String getRefreshToken(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                clientId, username, password
        );

        org.springframework.http.HttpEntity<String> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            
            if (response.getBody() != null) {
                return (String) response.getBody().get("refresh_token");
            }
        } catch (Exception e) {
            System.err.println("Failed to get refresh token: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Get full token response including access_token, refresh_token, expires_in, etc.
     */
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
            System.err.println("Failed to get token response: " + e.getMessage());
            throw new RuntimeException("Could not obtain token from Keycloak", e);
        }
    }

    /**
     * Refresh an access token using a refresh token
     */
    public String refreshAccessToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = String.format(
                "grant_type=refresh_token&client_id=%s&refresh_token=%s",
                clientId, refreshToken
        );

        org.springframework.http.HttpEntity<String> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            
            if (response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            System.err.println("Failed to refresh access token: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Get user info using an access token
     */
    public Map<String, Object> getUserInfo(String accessToken) {
        String userInfoUrl = String.format("%s/realms/%s/protocol/openid-connect/userinfo", 
                keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        org.springframework.http.HttpEntity<Void> entity = 
                new org.springframework.http.HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Failed to get user info: " + e.getMessage());
            throw new RuntimeException("Could not get user info from Keycloak", e);
        }
    }

    /**
     * Check if Keycloak is available
     */
    public boolean isKeycloakAvailable() {
        try {
            String healthUrl = String.format("%s/realms/%s", keycloakUrl, realm);
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create Authorization header value with Bearer token
     */
    public String createAuthorizationHeader(String username, String password) {
        String token = getAccessToken(username, password);
        return "Bearer " + token;
    }

    public void cleanupTestUsers() {


    }
}

