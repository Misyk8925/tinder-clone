package com.tinder.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

    private static final String PROFILES_ME_PATH = "/api/v1/profiles/me";

    private final SecurityService securityService;
    private final WebClient webClient;
    private final String profilesMeUrl;

    public PremiumOrAdminFilter(
            SecurityService securityService,
            WebClient.Builder webClientBuilder,
            @Value("${PROFILES_SERVICE_URL:http://localhost:8010}") String profilesServiceUrl
    ) {
        super(Object.class);
        this.securityService = securityService;
        this.webClient = webClientBuilder.build();
        this.profilesMeUrl = normalizeBaseUrl(profilesServiceUrl) + PROFILES_ME_PATH;
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
                                    .uri(profilesMeUrl)
                                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                                    .retrieve()
                                    .bodyToMono(ProfileResponse.class)
                                    .flatMap(profile -> {
                                        var mutated = exchange.getRequest().mutate()
                                                .header("X-User-Id", profile.profileId())
                                                .build();
                                        return chain.filter(exchange.mutate().request(mutated).build());
                                    })
                                    .onErrorResume(WebClientResponseException.class, ex -> {
                                        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
                                        exchange.getResponse().setStatusCode(status != null ? status : HttpStatus.BAD_GATEWAY);
                                        return exchange.getResponse().setComplete();
                                    })
                                    .onErrorResume(WebClientRequestException.class, ex -> {
                                        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                                        return exchange.getResponse().setComplete();
                                    });
                        });
            });
        };
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(String profileId) {}
}
