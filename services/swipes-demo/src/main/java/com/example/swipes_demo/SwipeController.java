package com.example.swipes_demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public Mono<ResponseEntity<Void>> swipe(@RequestBody @Valid SwipeDto dto) {
        return swipeService.sendSwipe(dto, false)
                .thenReturn(ResponseEntity.ok().build());
    }

    /** Only reachable via gateway's PremiumOrAdminFilter — no role check needed here. */
    @PostMapping("/super")
    public Mono<ResponseEntity<Void>> superLike(@RequestBody @Valid SwipeDto dto) {
        return swipeService.sendSwipe(dto, true)
                .thenReturn(ResponseEntity.ok().build());
    }
}
