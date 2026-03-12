package com.tinder.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        KeycloakJwtAuthenticationConverter keycloakConverter = new KeycloakJwtAuthenticationConverter();

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // Stripe sends its own signature header — no JWT involved
                        .pathMatchers("/api/v1/webhook/**").permitAll()
                        // Match-service paths use no /api/v1 prefix but still require authentication
                        .pathMatchers("/match/**", "/rest/conversations/**").authenticated()
                        // All other /api/** paths require a valid JWT
                        .pathMatchers("/api/**").authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakConverter)))
                .build();
    }
}




