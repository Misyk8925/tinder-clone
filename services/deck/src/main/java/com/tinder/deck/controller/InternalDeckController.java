package com.tinder.deck.controller;

import com.tinder.deck.service.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/deck")
@RequiredArgsConstructor
public class InternalDeckController {

    private final DeckService deckService;

    @PostMapping("/ensure")
    public Mono<ResponseEntity<Boolean>> ensure(@RequestParam UUID viewerId) {
        return deckService.ensureDeck(viewerId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError().body(false)));
    }
}
