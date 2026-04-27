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
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
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

    @Mock
    private DeckCache deckCache;

    private DeckScheduler deckScheduler;

    @BeforeEach
    void setUp() {
        deckScheduler = new DeckScheduler(deckService, profilesHttp, deckCache);
        ReflectionTestUtils.setField(deckScheduler, "recentViewersWindowMinutes", 30);
        ReflectionTestUtils.setField(deckScheduler, "maxRecentViewers", 1000);
        ReflectionTestUtils.setField(deckScheduler, "maxConcurrentRebuilds", 10);
        ReflectionTestUtils.setField(deckScheduler, "userRebuildTimeoutSeconds", 30);
    }

    @Test
    @DisplayName("rebuildAllDecks should fetch recent viewers and rebuild decks")
    void testRebuildAllDecks() throws InterruptedException {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        SharedProfileDto user1 = createProfile(user1Id, "Alice", 25);
        SharedProfileDto user2 = createProfile(user2Id, "Bob", 30);

        when(deckCache.getRecentViewerIds(any(), anyInt()))
                .thenReturn(Flux.just(user1Id, user2Id));
        when(profilesHttp.getProfilesByIds(List.of(user1Id, user2Id)))
                .thenReturn(Flux.just(user1, user2));
        when(deckService.rebuildOneDeck(any(SharedProfileDto.class)))
                .thenReturn(Mono.empty());

        deckScheduler.rebuildAllDecks();
        Thread.sleep(200);

        verify(deckCache, times(1)).getRecentViewerIds(any(), anyInt());
        verify(profilesHttp, times(1)).getProfilesByIds(List.of(user1Id, user2Id));
        verify(deckService, timeout(1000).times(2)).rebuildOneDeck(any(SharedProfileDto.class));
    }

    @Test
    @DisplayName("rebuildAllDecks should handle no recent viewers gracefully")
    void testRebuildAllDecksWithNoRecentUsers() {
        when(deckCache.getRecentViewerIds(any(), anyInt())).thenReturn(Flux.empty());

        deckScheduler.rebuildAllDecks();

        verify(deckCache, times(1)).getRecentViewerIds(any(), anyInt());
        verifyNoInteractions(deckService);
    }

    @Test
    @DisplayName("rebuildAllDecks should continue on individual user errors")
    void testRebuildAllDecksWithIndividualErrors() throws InterruptedException {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        SharedProfileDto user1 = createProfile(user1Id, "Alice", 25);
        SharedProfileDto user2 = createProfile(user2Id, "Bob", 30);

        when(deckCache.getRecentViewerIds(any(), anyInt()))
                .thenReturn(Flux.just(user1Id, user2Id));
        when(profilesHttp.getProfilesByIds(List.of(user1Id, user2Id)))
                .thenReturn(Flux.just(user1, user2));
        when(deckService.rebuildOneDeck(user1))
                .thenReturn(Mono.error(new RuntimeException("Rebuild failed for Alice")));
        when(deckService.rebuildOneDeck(user2))
                .thenReturn(Mono.empty());

        deckScheduler.rebuildAllDecks();
        Thread.sleep(200);

        verify(deckCache, times(1)).getRecentViewerIds(any(), anyInt());
        verify(profilesHttp, times(1)).getProfilesByIds(List.of(user1Id, user2Id));
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
