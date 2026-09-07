package com.tinder.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes identity headers that only the gateway may set, before anything else looks at the
 * request.
 * <p>
 * Downstream services treat {@code X-User-Id} as a statement of who the caller is, injected by
 * {@link PremiumOrAdminFilter} after the JWT has been validated. If a client could send that
 * header itself, any route that does not happen to overwrite it would carry an identity the
 * caller chose. Stripping it on the way in makes the header mean exactly one thing: "the gateway
 * verified this".
 */
@Component
public class TrustedHeaderStrippingFilter implements GlobalFilter, Ordered {

    static final List<String> GATEWAY_OWNED_HEADERS = List.of("X-User-Id");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean hasSpoofedHeader = GATEWAY_OWNED_HEADERS.stream()
                .anyMatch(header -> exchange.getRequest().getHeaders().containsKey(header));

        if (!hasSpoofedHeader) {
            return chain.filter(exchange);
        }

        ServerWebExchange sanitized = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> GATEWAY_OWNED_HEADERS.forEach(headers::remove))
                        .build())
                .build();
        return chain.filter(sanitized);
    }

    /** Must run before any filter that injects or reads these headers. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
