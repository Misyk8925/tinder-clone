package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeckService.
 */
@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private ProfilesHttp profilesHttp;

    @Mock
    private SwipesHttp swipesHttp;

    @Mock
    private DeckCache deckCache;

    @Mock
    private ScoringService scoringService;

    @Captor
    private ArgumentCaptor<List<Map.Entry<UUID, Double>>> deckCaptor;

    @Captor
    private ArgumentCaptor<Duration> durationCaptor;

    private DeckService deckService;

    private UUID viewerId;
    private SharedProfileDto viewerProfile;
    private SharedPreferencesDto preferences;



    @Test
    @DisplayName("Should rebuild deck with candidates that have no swipe history")
    void testRebuildDeckWithNoSwipeHistory() {
        // Given: 3 candidates from profiles service
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Alice", 28, null);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Bob", 32, null);
        SharedProfileDto candidate3 = createProfile(candidate3Id, "Charlie", 27, null);

        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.just(candidate1, candidate2, candidate3));

        // No swipe history for any candidates
        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // Mock scoring
        when(scoringService.score(eq(viewerProfile), eq(candidate1))).thenReturn(10.0);
        when(scoringService.score(eq(viewerProfile), eq(candidate2))).thenReturn(20.0);
        when(scoringService.score(eq(viewerProfile), eq(candidate3))).thenReturn(15.0);

        // Mock cache write
        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Cache should be written with all 3 candidates in descending score order
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), durationCaptor.capture());

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).hasSize(3);
        assertThat(deck.get(0).getKey()).isEqualTo(candidate2Id); // score 20.0
        assertThat(deck.get(0).getValue()).isEqualTo(20.0);
        assertThat(deck.get(1).getKey()).isEqualTo(candidate3Id); // score 15.0
        assertThat(deck.get(1).getValue()).isEqualTo(15.0);
        assertThat(deck.get(2).getKey()).isEqualTo(candidate1Id); // score 10.0
        assertThat(deck.get(2).getValue()).isEqualTo(10.0);

        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("Should filter out candidates with existing swipe history")
    void testRebuildDeckFilteringSwipedCandidates() {
        // Given: 3 candidates from profiles service
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Alice", 28, null);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Bob", 32, null);
        SharedProfileDto candidate3 = createProfile(candidate3Id, "Charlie", 27, null);

        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.just(candidate1, candidate2, candidate3));

        // Candidate2 has swipe history (should be filtered out)
        Map<UUID, Boolean> swipeHistory = new HashMap<>();
        swipeHistory.put(candidate2Id, true);

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(swipeHistory));

        // Mock scoring for remaining candidates
        when(scoringService.score(eq(viewerProfile), eq(candidate1))).thenReturn(10.0);
        when(scoringService.score(eq(viewerProfile), eq(candidate3))).thenReturn(15.0);

        // Mock cache write
        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Cache should only contain candidates without swipe history
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).hasSize(2);
        assertThat(deck.stream().map(Map.Entry::getKey))
                .containsExactly(candidate3Id, candidate1Id) // Descending score order
                .doesNotContain(candidate2Id);
    }

    @Test
    @DisplayName("Should handle empty candidate list from profiles service")
    void testRebuildDeckWithNoCandidates() {
        // Given: No candidates from profiles service
        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.empty());

        // Mock cache write
        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Cache should be written with empty deck
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).isEmpty();
    }

    @Test
    @DisplayName("Should handle profiles service error gracefully")
    void testRebuildDeckWithProfilesServiceError() {
        // Given: Profiles service throws error
        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.error(new RuntimeException("Profiles service error")));

        // Mock cache write
        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Cache should be written with empty deck (error was handled)
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).isEmpty();
    }

    @Test
    @DisplayName("Should handle swipes service error gracefully")
    void testRebuildDeckWithSwipesServiceError() {
        // Given: Candidates from profiles service
        UUID candidate1Id = UUID.randomUUID();
        SharedProfileDto candidate1 = createProfile(candidate1Id, "Alice", 28, null);

        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.just(candidate1));

        // Swipes service throws error
        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.error(new RuntimeException("Swipes service error")));

        // Mock scoring
        when(scoringService.score(eq(viewerProfile), eq(candidate1))).thenReturn(10.0);

        // Mock cache write
        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: All candidates should be included (error returns empty map, so no filtering)
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).hasSize(1);
        assertThat(deck.get(0).getKey()).isEqualTo(candidate1Id);
    }

    @Test
    @DisplayName("Should limit deck to perUserLimit")
    void testRebuildDeckWithLimitEnforcement() {
        // Given: More candidates than perUserLimit (set to 500)
        ReflectionTestUtils.setField(deckService, "perUserLimit", 3);

        List<SharedProfileDto> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            SharedProfileDto profile = createProfile(id, "User" + i, 25 + i, null);
            candidates.add(profile);
            when(scoringService.score(eq(viewerProfile), eq(profile))).thenReturn((double) i);
        }

        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.fromIterable(candidates));

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Deck should be limited to 3 candidates (top scores)
        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> deck = deckCaptor.getValue();
        assertThat(deck).hasSize(3);
        // Should have top 3 scores: 9.0, 8.0, 7.0
        assertThat(deck.get(0).getValue()).isEqualTo(9.0);
        assertThat(deck.get(1).getValue()).isEqualTo(8.0);
        assertThat(deck.get(2).getValue()).isEqualTo(7.0);
    }

    @Test
    @DisplayName("Should process candidates in batches for swipe checking")
    void testRebuildDeckProcessesBatches() {
        // Given: 250 candidates (should be split into 2 batches of 200 and 50)
        List<SharedProfileDto> candidates = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            UUID id = UUID.randomUUID();
            SharedProfileDto profile = createProfile(id, "User" + i, 25, null);
            candidates.add(profile);
            when(scoringService.score(eq(viewerProfile), eq(profile))).thenReturn(10.0);
        }

        when(profilesHttp.searchProfiles(eq(viewerId), eq(preferences), eq(2000)))
                .thenReturn(Flux.fromIterable(candidates));

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When: Rebuilding deck
        StepVerifier.create(deckService.rebuildOneDeck(viewerProfile))
                .verifyComplete();

        // Then: Swipes service should be called twice (2 batches)
        verify(swipesHttp, times(2)).betweenBatch(eq(viewerId), anyList());
    }

    // Helper method to create profile
    private SharedProfileDto createProfile(UUID id, String name, int age, SharedPreferencesDto prefs) {
        SharedLocationDto location = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "City",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        );

        if (prefs == null) {
            prefs = new SharedPreferencesDto(18, 50, "ANY", 100);
        }
        return new SharedProfileDto(id, name, age, "Bio", "City", true, location, prefs, false, List.of());
    }
}
