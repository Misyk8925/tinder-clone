package com.tinder.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProfilesInternalAuthFilter extends AbstractGatewayFilterFactory<Object> {

    public static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    public static final String USER_SUBJECT_HEADER = "X-User-Subject";

    private final String internalAuthSecret;

    public ProfilesInternalAuthFilter(
            @Value("${profiles.internal-auth-secret:}") String internalAuthSecret
    ) {
        super(Object.class);
        this.internalAuthSecret = internalAuthSecret == null ? "" : internalAuthSecret;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            var sanitized = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(INTERNAL_AUTH_HEADER);
                        headers.remove(USER_SUBJECT_HEADER);
                    })
                    .build();
            var sanitizedExchange = exchange.mutate().request(sanitized).build();

            if (internalAuthSecret.isBlank()) {
                return chain.filter(sanitizedExchange);
            }

            return resolveVerifiedSubject(sanitizedExchange)
                    .flatMap(subject -> {
                        var mutated = sanitizedExchange.getRequest().mutate()
                                .headers(headers -> {
                                    headers.remove(HttpHeaders.AUTHORIZATION);
                                    headers.add(INTERNAL_AUTH_HEADER, internalAuthSecret);
                                    headers.add(USER_SUBJECT_HEADER, subject);
                                })
                                .build();
                        return chain.filter(sanitizedExchange.mutate().request(mutated).build())
                                .thenReturn(Boolean.TRUE);
                    })
                    .defaultIfEmpty(Boolean.FALSE)
                    .flatMap(forwarded -> forwarded ? Mono.empty() : unauthorized(sanitizedExchange));
        }, -80);
    }

    private Mono<String> resolveVerifiedSubject(org.springframework.web.server.ServerWebExchange exchange) {
        Object verified = exchange.getAttribute(ProfileDeckJwtAuthFilter.VERIFIED_SUBJECT_ATTRIBUTE);
        if (verified instanceof String subject && !subject.isBlank()) {
            return Mono.just(subject);
        }

        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> jwtAuth.getToken().getSubject())
                .filter(subject -> subject != null && !subject.isBlank());
    }

    private Mono<Void> unauthorized(org.springframework.web.server.ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
