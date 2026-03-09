package com.tinder.subscriptions.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration("SubscriptionsSecurityConfig")
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Reuse the same JwtAuthConverter pattern as the profiles service —
     * maps Keycloak realm_access + resource_access roles to ROLE_* GrantedAuthority.
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationTokenConverter() {
        return new JwtAuthConverter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Stripe webhooks are authenticated by Stripe-Signature HMAC header,
                        // not by Bearer JWT — must stay open to Stripe's servers
                        .requestMatchers("/api/v1/webhook").permitAll()
                        // All other endpoints require a valid JWT
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationTokenConverter())
                        )
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}




