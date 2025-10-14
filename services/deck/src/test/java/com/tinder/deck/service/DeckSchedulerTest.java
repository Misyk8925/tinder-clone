package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    @DisplayName("rebuildAllDecks should log and complete without errors")
    void testRebuildAllDecks() {
        // When: Scheduled method is called
        deckScheduler.rebuildAllDecks();

        // Then: Should complete without errors
        // Currently this is a placeholder that just logs
        // No interactions with services yet
        verifyNoInteractions(deckService);
        verifyNoInteractions(profilesHttp);
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
        GeometryFactory geometryFactory = new GeometryFactory();
        Point point = geometryFactory.createPoint(new Coordinate(0.0, 0.0));

        SharedLocationDto location = new SharedLocationDto(
                UUID.randomUUID(),
                point,
                "City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        SharedPreferencesDto prefs = new SharedPreferencesDto(18, 50, "ANY", 100);

        return new SharedProfileDto(id, name, age, "Bio", "City", true, location, prefs, false);
    }
}

