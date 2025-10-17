package com.tinder.deck.controlller;

import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.DeckScheduler;
import com.tinder.deck.service.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin/monitoring endpoints for Deck Service
 * This service primarily runs as a background worker
 * Client applications should use Profiles Service /api/v1/profiles/deck endpoint
 */
@RestController
@RequestMapping("/api/v1/admin/deck")
@RequiredArgsConstructor
public class DeckController {

    private final DeckCache cache;
    private final DeckService deckService;
    private final DeckScheduler deckScheduler;

    /**
     * Check if deck exists for a user
     */

    @GetMapping("/manual-rebuild")
    public void manualRebuild() {
        deckScheduler.rebuildAllDecks();
    }
    @GetMapping("/exists")
    public Mono<ResponseEntity<Boolean>> exists(@RequestParam UUID viewerId) {
        return cache.size(viewerId)
                .map(size -> ResponseEntity.ok(size > 0));
    }

    /**
     * Get deck size for a user
     */
    @GetMapping("/size")
    public Mono<ResponseEntity<Long>> size(@RequestParam UUID viewerId) {
        return cache.size(viewerId)
                .map(ResponseEntity::ok);
    }

    /**
     * Manually trigger deck rebuild for a user (admin only)
     */
    @PostMapping("/rebuild")
    public Mono<ResponseEntity<String>> rebuild(@RequestParam UUID viewerId) {
        // TODO: Fetch viewer profile from Profiles Service
        // For now, return not implemented
        return Mono.just(ResponseEntity.status(501)
                .body("Manual rebuild not yet implemented. Use scheduled rebuilds."));
    }

    /**
     * Invalidate (delete) deck for a user
     */
    @DeleteMapping
    public Mono<ResponseEntity<String>> invalidate(@RequestParam UUID viewerId) {
        return cache.invalidate(viewerId)
                .map(deleted -> ResponseEntity.ok("Deleted " + deleted + " keys"));
    }
}