package com.tinder.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that allows only premium users and admins through.
 *
 * On success, calls the profiles service to resolve the caller's profileId
 * (profiles use an auto-generated UUID, separate from the Keycloak sub) and
 * injects it as X-User-Id so downstream services receive the correct identifier.
 */
@Component
public class PremiumOrAdminFilter extends AbstractGatewayFilterFactory<Object> {

    private static final String PROFILES_ME_URL = "http://localhost:8010/api/v1/profiles/me";

    private final SecurityService securityService;
    private final WebClient webClient;

    public PremiumOrAdminFilter(SecurityService securityService, WebClient.Builder webClientBuilder) {
        super(Object.class);
        this.securityService = securityService;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            Mono<Boolean> hasAccess = securityService.isPremiumUser()
                    .flatMap(isPremium -> isPremium ? Mono.just(true) : securityService.isAdmin());

            return hasAccess.flatMap(allowed -> {
                if (!allowed) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }

                return ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> (JwtAuthenticationToken) ctx.getAuthentication())
                        .flatMap(jwtAuth -> {
                            String bearerToken = "Bearer " + jwtAuth.getToken().getTokenValue();

                            return webClient.get()
                                    .uri(PROFILES_ME_URL)
                                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                                    .retrieve()
                                    .bodyToMono(ProfileResponse.class)
                                    .flatMap(profile -> {
                                        var mutated = exchange.getRequest().mutate()
                                                .header("X-User-Id", profile.profileId())
                                                .build();
                                        return chain.filter(exchange.mutate().request(mutated).build());
                                    });
                        });
            });
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(String profileId) {}
}
