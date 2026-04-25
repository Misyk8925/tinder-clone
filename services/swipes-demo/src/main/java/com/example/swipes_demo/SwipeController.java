package com.example.swipes_demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private static final ResponseEntity<Void> ACCEPTED = ResponseEntity.accepted().build();

    private final SwipeService swipeService;
    private final InternalAuthVerifier internalAuthVerifier;

    @PostMapping(headers = InternalAuthVerifier.HEADER_NAME)
    public Mono<ResponseEntity<Void>> swipeInternal(@RequestBody SwipeDto dto,
                                                    @AuthenticationPrincipal Jwt jwt,
                                                    @RequestHeader(name = InternalAuthVerifier.HEADER_NAME)
                                                    String internalAuth,
                                                    ServerWebExchange exchange) {
        boolean internalRequest = isInternalRequest(exchange, internalAuth);
        if (!internalRequest && jwt == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal auth"));
        }
        return swipeService.sendSwipe(dto, false, jwt, internalRequest)
                .thenReturn(ACCEPTED);
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> swipe(@RequestBody @Valid SwipeDto dto,
                                            @AuthenticationPrincipal Jwt jwt) {
        return swipeService.sendSwipe(dto, false, jwt, false)
                .thenReturn(ACCEPTED);
    }

    /** Only reachable via gateway's PremiumOrAdminFilter — no role check needed here. */
    @PostMapping(value = "/super", headers = InternalAuthVerifier.HEADER_NAME)
    public Mono<ResponseEntity<Void>> superLikeInternal(@RequestBody SwipeDto dto,
                                                        @AuthenticationPrincipal Jwt jwt,
                                                        @RequestHeader(name = InternalAuthVerifier.HEADER_NAME)
                                                        String internalAuth,
                                                        ServerWebExchange exchange) {
        boolean internalRequest = isInternalRequest(exchange, internalAuth);
        if (!internalRequest && jwt == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal auth"));
        }
        return swipeService.sendSwipe(dto, true, jwt, internalRequest)
                .thenReturn(ACCEPTED);
    }

    /** Only reachable via gateway's PremiumOrAdminFilter — no role check needed here. */
    @PostMapping("/super")
    public Mono<ResponseEntity<Void>> superLike(@RequestBody @Valid SwipeDto dto,
                                                @AuthenticationPrincipal Jwt jwt) {
        return swipeService.sendSwipe(dto, true, jwt, false)
                .thenReturn(ACCEPTED);
    }

    private boolean isInternalRequest(ServerWebExchange exchange, String internalAuth) {
        return Boolean.TRUE.equals(exchange.getAttribute(InternalAuthVerifier.ATTRIBUTE_AUTHENTICATED))
                || internalAuthVerifier.isValid(internalAuth);
    }
}
