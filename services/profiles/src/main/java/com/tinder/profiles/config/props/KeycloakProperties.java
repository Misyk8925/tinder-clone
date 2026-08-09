package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Keycloak coordinates used for Admin REST API calls ({@code keycloak.*}).
 * The admin credentials authenticate against the {@code master} realm.
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(

        @DefaultValue("http://localhost:9080") String keycloakUrl,

        @DefaultValue("spring") String realm,

        @DefaultValue("spring-app") String clientId,

        @DefaultValue("admin") String adminUsername,

        @DefaultValue("admin") String adminPassword
) {
}
