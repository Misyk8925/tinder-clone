package com.tinder.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TracePathFilter implements GlobalFilter, Ordered {
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var req = exchange.getRequest();
        var before = req.getURI().getPath();
        return chain.filter(exchange).doOnSubscribe(s ->
                        System.out.println("[SCG] BEFORE path=" + before +
                                " x-original=" + req.getHeaders().getFirst("X-Original-URI")))
                .doOnSuccess(v -> {
                    var after = exchange.getRequest().getURI().getPath();
                    System.out.println("[SCG] AFTER  path=" + after);
                });
    }
    @Override public int getOrder() { return -1; }
}
