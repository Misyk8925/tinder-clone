package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Background scheduler that rebuilds decks for active users
 * Runs periodically to keep decks fresh in Redis cache
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeckScheduler {

    private final DeckService deckService;
    private final ProfilesHttp profilesHttp;

    /**
     * Rebuild decks for all active users
     * Runs every 10 minutes
     * For now, this is a placeholder
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void rebuildAllDecks() {
        log.info("Starting scheduled deck rebuild...");


        Flux<SharedProfileDto> activeUsers = profilesHttp.getActiveUsers();
        log.info("Active users: {}", activeUsers.collectList().block());

        activeUsers
            .timeout(Duration.ofSeconds(60))
            .doOnError(e -> log.error("Error getting active users", e))
            .doOnNext(viewer -> this.rebuildDeckForUser(viewer))
            .doOnComplete(() -> log.info("Deck rebuilding completed"))
            .subscribe();
    }

    /**
     * Rebuild deck for a single user
     * Can be called manually or from a queue
     */
    public void rebuildDeckForUser(SharedProfileDto viewer) {
        log.info("Rebuilding deck for user: {}", viewer.id());

        deckService.rebuildOneDeck(viewer)
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(v -> log.info("Successfully rebuilt deck for user: {}", viewer.id()))
                .doOnError(e -> log.error("Failed to rebuild deck for user: {}", viewer.id(), e))
                .subscribe();
    }
}
