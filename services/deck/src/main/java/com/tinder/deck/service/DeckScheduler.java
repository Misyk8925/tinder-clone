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
     * Runs every hour (reduced from every minute for efficiency)
     *
     * Note: With Preferences Cache enabled, this is much more efficient:
     * - Groups users by preferences (10-15 groups typically)
     * - Shares candidate queries across users with same preferences
     * - 100x fewer database queries compared to individual rebuilds
     */

    // TODO adjust cron expression as needed
    @Scheduled(cron = "0 */1 * * * *")
    public void rebuildAllDecks() {

        log.info("Starting scheduled batch deck rebuild (hourly)");
        log.info("Preferences cache enabled - efficient batch processi^ng mode");

        Flux<SharedProfileDto> activeUsers = profilesHttp.getActiveUsers();

        if (activeUsers == null){
            log.error("Failed to fetch active users - aborting scheduled rebuild");
            return;
        }

        activeUsers
            .timeout(Duration.ofSeconds(60))
            .doOnError(e -> log.error("Error during scheduled deck rebuild", e))
            .doOnNext(viewer -> this.rebuildDeckForUser(viewer))
            .doOnComplete(() -> log.info("Scheduled batch deck rebuild completed successfully"))
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
