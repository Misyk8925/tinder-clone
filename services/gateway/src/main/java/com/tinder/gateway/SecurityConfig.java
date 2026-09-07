package com.tinder.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /**
     * Browser origins allowed to call the API with credentials. Kept as an explicit list —
     * with {@code allowCredentials(true)} a wildcard would let any site read authenticated
     * responses on a visitor's behalf.
     */
    static final List<String> DEFAULT_ALLOWED_ORIGINS =
            List.of("http://localhost:4200", "https://lunari.misyk.tech");

    @Value("${gateway.cors.allowed-origins:http://localhost:4200,https://lunari.misyk.tech}")
    private List<String> allowedOrigins = DEFAULT_ALLOWED_ORIGINS;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                allowedOrigins == null || allowedOrigins.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "Stripe-Signature"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Deck reads are authorised by deck-read itself, which resolves the viewer from the JWT
     * subject. The gateway still *authenticates* the token here so that a verified principal
     * exists for rate limiting — otherwise the limiter would have nothing trustworthy to key on.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain deckReadSecurityFilterChain(ServerHttpSecurity http) {
        KeycloakJwtAuthenticationConverter keycloakConverter = new KeycloakJwtAuthenticationConverter();

        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/deck", "/api/v2/deck"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakConverter)))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        KeycloakJwtAuthenticationConverter keycloakConverter = new KeycloakJwtAuthenticationConverter();

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        // Preflight requests must pass without a JWT
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Stripe sends its own signature header — no JWT involved
                        .pathMatchers("/api/v1/webhook/**").permitAll()
                        // Deck-read validates this token itself and resolves the viewer from its subject.
                        .pathMatchers("/api/v1/deck", "/api/v2/deck").permitAll()
                        // WebSocket upgrade — JWT auth handled inside the match service via STOMP channel interceptor
                        .pathMatchers("/ws", "/ws/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Deck maintenance endpoints act on any viewer's deck: operators only.
                        // The rate-limit filter alone cannot carry this — authorization belongs here.
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
