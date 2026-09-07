package com.tinder.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that allows only premium users and admins through.
 * <p>
 * It decides entitlement and nothing else. It used to also resolve the caller's profile ID and
 * pass it downstream as {@code X-User-Id}, but a header is only trustworthy on the path that
 * sets it: a service reached directly would take it at face value. Downstream services now
 * derive the caller's profile from the bearer token themselves, which holds however the request
 * arrived, so the lookup here has been dropped along with the per-request call it cost.
 */
@Component
public class PremiumOrAdminFilter extends AbstractGatewayFilterFactory<Object> {

    private final SecurityService securityService;

    public PremiumOrAdminFilter(SecurityService securityService) {
        super(Object.class);
        this.securityService = securityService;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> securityService.isPremiumUser()
                .flatMap(isPremium -> isPremium ? Mono.just(true) : securityService.isAdmin())
                .flatMap(allowed -> {
                    if (!allowed) {
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }
}
