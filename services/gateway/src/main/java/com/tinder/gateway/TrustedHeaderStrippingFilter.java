package com.tinder.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes identity headers that a client must never be able to set, before anything else looks
 * at the request.
 * <p>
 * Services derive the caller's identity from the bearer token rather than from headers, so
 * nothing downstream reads {@code X-User-Id} today. This filter keeps it that way: it guarantees
 * the header cannot arrive from outside, so a service that starts trusting it later cannot be
 * fooled by a caller who simply sent one.
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
