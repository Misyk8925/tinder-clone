package com.example.swipes_demo;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class InternalSwipeFastPathFilter implements WebFilter, Ordered {

    private static final String SWIPES_PATH = "/api/v1/swipes";

    private final InternalAuthVerifier internalAuthVerifier;
    private final SwipeService swipeService;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isFastPathRequest(exchange)) {
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    return swipeService.sendTrustedInternalSwipe(body, false);
                })
                .then(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
                    return exchange.getResponse().setComplete();
                }));
    }

    private boolean isFastPathRequest(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return false;
        }
        if (!SWIPES_PATH.equals(exchange.getRequest().getPath().pathWithinApplication().value())) {
            return false;
        }

        String internalAuth = exchange.getRequest()
                .getHeaders()
                .getFirst(InternalAuthVerifier.HEADER_NAME);
        return internalAuthVerifier.isValid(internalAuth);
    }
}
