package com.example.swipes_demo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;


@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalAuthVerifier internalAuthVerifier;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().access(this::jwtOrInternalAuth)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    private Mono<AuthorizationResult> jwtOrInternalAuth(Mono<Authentication> authentication,
                                                        AuthorizationContext context) {
        String internalAuth = context.getExchange()
                .getRequest()
                .getHeaders()
                .getFirst(InternalAuthVerifier.HEADER_NAME);

        if (internalAuthVerifier.isValid(internalAuth)) {
            context.getExchange()
                    .getAttributes()
                    .put(InternalAuthVerifier.ATTRIBUTE_AUTHENTICATED, Boolean.TRUE);
            return Mono.just((AuthorizationResult) new AuthorizationDecision(true));
        }

        return authentication
                .map(Authentication::isAuthenticated)
                .map(authenticated -> (AuthorizationResult) new AuthorizationDecision(authenticated))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
