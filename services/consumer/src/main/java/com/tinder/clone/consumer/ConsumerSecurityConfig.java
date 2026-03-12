package com.tinder.clone.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Set;

/**
 * Security configuration for consumer service.
 * /between/batch is protected by mTLS — only deck-service CN is allowed.
 */
@Configuration
@EnableWebSecurity
public class ConsumerSecurityConfig {

    @Value("${mtls.allowed-client-cns:deck-service}")
    private List<String> allowedClientCns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // X.509 mutual TLS: extract CN from client certificate subject
                .x509(x509 -> x509
                        .userDetailsService(mtlsUserDetailsService())
                )
                .authorizeHttpRequests(auth -> auth
                        // Require authenticated internal client for swipe batch endpoint
                        .requestMatchers("/between/batch").hasRole("INTERNAL_CLIENT")
                        // Allow all other endpoints (e.g. actuator, health)
                        .anyRequest().permitAll()
                )
                .build();
    }

    @Bean
    public UserDetailsService mtlsUserDetailsService() {
        Set<String> allowed = Set.copyOf(allowedClientCns);
        return cn -> {
            if (!allowed.contains(cn)) {
                throw new UsernameNotFoundException(
                        "Certificate CN '" + cn + "' is not authorized");
            }
            return new User(cn, "", List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_CLIENT")));
        };
    }
}


