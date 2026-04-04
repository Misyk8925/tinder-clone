package com.example.swipes_demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService swipeService;

    @PostMapping
    public Mono<ResponseEntity<Void>> swipe(@RequestBody @Valid SwipeDto dto,
                                            @AuthenticationPrincipal Jwt jwt) {
        return swipeService.sendSwipe(dto, false, jwt)
                .thenReturn(ResponseEntity.ok().build());
    }

    /** Only reachable via gateway's PremiumOrAdminFilter — no role check needed here. */
    @PostMapping("/super")
    public Mono<ResponseEntity<Void>> superLike(@RequestBody @Valid SwipeDto dto,
                                                @AuthenticationPrincipal Jwt jwt) {
        return swipeService.sendSwipe(dto, true, jwt)
                .thenReturn(ResponseEntity.ok().build());
    }
}
