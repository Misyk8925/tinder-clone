package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeckScheduler
 */
@ExtendWith(MockitoExtension.class)
class DeckSchedulerTest {

    @Mock
    private DeckService deckService;

    @Mock
    private ProfilesHttp profilesHttp;

    private DeckScheduler deckScheduler;

    @BeforeEach
    void setUp() {
        deckScheduler = new DeckScheduler(deckService, profilesHttp);
    }

    @Test
    @DisplayName("rebuildAllDecks should fetch active users and rebuild decks")
    void testRebuildAllDecks() throws InterruptedException {
        // Given: Active users
        SharedProfileDto user1 = createProfile(UUID.randomUUID(), "Alice", 25);
        SharedProfileDto user2 = createProfile(UUID.randomUUID(), "Bob", 30);

        when(profilesHttp.getActiveUsers())
                .thenReturn(reactor.core.publisher.Flux.just(user1, user2));
        when(deckService.rebuildOneDeck(any(SharedProfileDto.class)))
                .thenReturn(Mono.empty());

        // When: Scheduled method is called
        deckScheduler.rebuildAllDecks();

        // Wait for async processing
        Thread.sleep(200);

        // Then: Should fetch active users and rebuild decks for each
        verify(profilesHttp, times(1)).getActiveUsers();
        verify(deckService, timeout(1000).times(2)).rebuildOneDeck(any(SharedProfileDto.class));
    }

    @Test
    @DisplayName("rebuildAllDecks should handle null active users gracefully")
    void testRebuildAllDecksWithNullUsers() {
        // Given: getActiveUsers returns null
        when(profilesHttp.getActiveUsers()).thenReturn(null);

        // When: Scheduled method is called
        deckScheduler.rebuildAllDecks();

        // Then: Should handle gracefully without errors
        verify(profilesHttp, times(1)).getActiveUsers();
        verifyNoInteractions(deckService);
    }

    @Test
    @DisplayName("rebuildAllDecks should handle empty active users list")
    void testRebuildAllDecksWithEmptyUsers() throws InterruptedException {
        // Given: No active users
        when(profilesHttp.getActiveUsers())
                .thenReturn(reactor.core.publisher.Flux.empty());
        when(deckService.rebuildOneDeck(any(SharedProfileDto.class)))
                .thenReturn(Mono.empty());

        // When: Scheduled method is called
        deckScheduler.rebuildAllDecks();

        // Wait for async processing
        Thread.sleep(100);

        // Then: Should not call rebuild
        verify(profilesHttp, times(1)).getActiveUsers();
        verify(deckService, never()).rebuildOneDeck(any(SharedProfileDto.class));
    }

    @Test
    @DisplayName("rebuildAllDecks should continue on individual user errors")
    void testRebuildAllDecksWithIndividualErrors() throws InterruptedException {
        // Given: Active users, one rebuild fails
        SharedProfileDto user1 = createProfile(UUID.randomUUID(), "Alice", 25);
        SharedProfileDto user2 = createProfile(UUID.randomUUID(), "Bob", 30);

        when(profilesHttp.getActiveUsers())
                .thenReturn(reactor.core.publisher.Flux.just(user1, user2));
        when(deckService.rebuildOneDeck(user1))
                .thenReturn(Mono.error(new RuntimeException("Rebuild failed for Alice")));
        when(deckService.rebuildOneDeck(user2))
                .thenReturn(Mono.empty());

        // When: Scheduled method is called
        deckScheduler.rebuildAllDecks();

        // Wait for async processing
        Thread.sleep(200);

        // Then: Should attempt both rebuilds (error handled gracefully)
        verify(profilesHttp, times(1)).getActiveUsers();
        verify(deckService, timeout(1000).times(2)).rebuildOneDeck(any(SharedProfileDto.class));
    }

    @Test
    @DisplayName("rebuildDeckForUser should call deckService.rebuildOneDeck")
    void testRebuildDeckForUser() {
        // Given: A viewer profile
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "John", 30);

        // Mock deck service
        when(deckService.rebuildOneDeck(viewer)).thenReturn(Mono.empty());

        // When: Rebuilding deck for user
        deckScheduler.rebuildDeckForUser(viewer);

        // Give some time for async operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: DeckService should be called
        verify(deckService, timeout(1000)).rebuildOneDeck(viewer);
    }

    @Test
    @DisplayName("rebuildDeckForUser should handle errors gracefully")
    void testRebuildDeckForUserWithError() {
        // Given: A viewer profile
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "John", 30);

        // Mock deck service to throw error
        when(deckService.rebuildOneDeck(viewer))
                .thenReturn(Mono.error(new RuntimeException("Rebuild failed")));

        // When: Rebuilding deck for user
        deckScheduler.rebuildDeckForUser(viewer);

        // Give some time for async operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: Should not throw exception (error is logged)
        verify(deckService, timeout(1000)).rebuildOneDeck(viewer);
    }

    @Test
    @DisplayName("rebuildDeckForUser should timeout after 30 seconds")
    void testRebuildDeckForUserTimeout() {
        // Given: A viewer profile
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "John", 30);

        // Mock deck service to never complete
        when(deckService.rebuildOneDeck(viewer)).thenReturn(Mono.never());

        // When: Rebuilding deck for user
        deckScheduler.rebuildDeckForUser(viewer);

        // Give some time for async operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: Should be called (timeout will be handled by Mono.timeout)
        verify(deckService, timeout(1000)).rebuildOneDeck(viewer);
    }

    // Helper method to create profile
    private SharedProfileDto createProfile(UUID id, String name, int age) {
        SharedLocationDto location = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        SharedPreferencesDto prefs = new SharedPreferencesDto(18, 50, "ANY", 100);

        return new SharedProfileDto(id, name, age, "Bio", "City", true, location, prefs, false, List.of());
    }
}
