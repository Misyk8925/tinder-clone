package com.tinder.profiles.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;

@Configuration("ProfilesSecurityConfig")
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    private final InternalAuthVerifier internalAuthVerifier;

    public SecurityConfig(InternalAuthVerifier internalAuthVerifier) {
        this.internalAuthVerifier = internalAuthVerifier;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAbstractAuthenticationTokenConverter() {
        return new JwtAuthConverter();
    }

    /**
     * Security chain for internal endpoints (/internal/**).
     * mTLS is enforced at the Tomcat connector level (port 8011, clientAuth=need).
     * Here we additionally verify the client CN matches expected service identity.
     * Runs before the public chain (higher @Order).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        // Match requests to /internal/** path prefix
        RequestMatcher internalMatcher = request -> {
            String path = request.getServletPath();
            return path != null && path.contains("/internal/");
        };
        return http
                .securityMatcher(internalMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // X.509 client-cert auth: principal CN must match "deck-service"
                .x509(x509 -> x509
                        .userDetailsService(new MtlsUserDetailsService())
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("INTERNAL_CLIENT")
                )
                .build();
    }

    /**
     * Deck reads can be called either directly with JWT or from the gateway
     * with internal auth and a trusted user-subject header.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain deckSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .securityMatcher(new AntPathRequestMatcher("/api/v1/profiles/deck"))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().access((authentication, context) ->
                                jwtOrInternalAuth(authentication, context.getRequest()))
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAbstractAuthenticationTokenConverter())
                        )
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Security chain for all public endpoints (JWT bearer token).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAbstractAuthenticationTokenConverter())
                        )
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    private AuthorizationDecision jwtOrInternalAuth(Supplier<Authentication> authentication,
                                                    HttpServletRequest request) {
        if (internalAuthVerifier.isValid(request.getHeader(InternalAuthVerifier.HEADER_NAME))) {
            return new AuthorizationDecision(true);
        }

        Authentication current = authentication.get();
        boolean authenticated = current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken);
        return new AuthorizationDecision(authenticated);
    }
}
