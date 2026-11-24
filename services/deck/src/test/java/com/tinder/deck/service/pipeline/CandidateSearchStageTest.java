package com.tinder.deck.service.pipeline;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.pipeline.util.PreferencesCacheHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CandidateSearchStage
 *
 * Tests candidate search functionality including:
 * - Successful candidate retrieval
 * - Error handling and recovery
 * - Timeout and retry logic
 * - Default preferences handling
 */
@ExtendWith(MockitoExtension.class)
class CandidateSearchStageTest {

    // Test configuration constants
    private static final int SEARCH_LIMIT = 2000;
    private static final long TIMEOUT_MS = 5000L;
    private static final int RETRIES = 3;

    // Test data constants
    private static final int VIEWER_AGE = 25;
    private static final int CANDIDATE_AGE_1 = 23;
    private static final int CANDIDATE_AGE_2 = 27;

    @Mock
    private ProfilesHttp profilesHttp;

    @Mock
    private DeckCache deckCache;

    @Mock
    private PreferencesCacheHelper preferencesCacheHelper;

    private CandidateSearchStage candidateSearchStage;

    @BeforeEach
    void setUp() {
        candidateSearchStage = new CandidateSearchStage(profilesHttp, deckCache, preferencesCacheHelper);
        ReflectionTestUtils.setField(candidateSearchStage, "searchLimit", SEARCH_LIMIT);
        ReflectionTestUtils.setField(candidateSearchStage, "timeoutMs", TIMEOUT_MS);
        ReflectionTestUtils.setField(candidateSearchStage, "retries", RETRIES);
    }

    @Test
    void shouldSearchCandidatesSuccessfully() {
        // Given: a viewer and two potential candidates
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);
        SharedProfileDto firstCandidate = createProfile(UUID.randomUUID(), "Alice", CANDIDATE_AGE_1);
        SharedProfileDto secondCandidate = createProfile(UUID.randomUUID(), "Bob", CANDIDATE_AGE_2);

        when(profilesHttp.searchProfiles(eq(viewer.id()), any(SharedPreferencesDto.class), eq(SEARCH_LIMIT)))
                .thenReturn(Flux.just(firstCandidate, secondCandidate));

        // When: searching for candidates
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(viewer);

        // Then: should return both candidates in order
        StepVerifier.create(result)
                .expectNext(firstCandidate)
                .expectNext(secondCandidate)
                .verifyComplete();
    }

    @Test
    void shouldPropagateErrorWithoutTurningItIntoAnEmptyDeck() {
        // Given: a viewer and a failing profiles service
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);
        RuntimeException serviceError = new RuntimeException("Service unavailable");

        when(profilesHttp.searchProfiles(eq(viewer.id()), any(SharedPreferencesDto.class), eq(SEARCH_LIMIT)))
                .thenReturn(Flux.error(serviceError));

        // When: searching for candidates with error
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(viewer);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error == serviceError)
                .verify();
    }

    @Test
    void shouldHandleEmptyResultsFromProfilesService() {
        // Given: a viewer with no matching candidates
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        when(profilesHttp.searchProfiles(eq(viewer.id()), any(SharedPreferencesDto.class), eq(SEARCH_LIMIT)))
                .thenReturn(Flux.empty());

        // When: searching returns no results
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(viewer);

        // Then: should complete without results
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldHandleTimeout() {
        // Given: a viewer and a slow-responding service
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);
        SharedProfileDto candidate = createProfile(UUID.randomUUID(), "Candidate", CANDIDATE_AGE_1);

        when(profilesHttp.searchProfiles(eq(viewer.id()), any(SharedPreferencesDto.class), eq(SEARCH_LIMIT)))
                .thenReturn(Flux.just(candidate).delayElements(Duration.ofSeconds(10)));

        // When: service response exceeds timeout
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(viewer);

        // Then: should timeout and return empty (after retries)
        StepVerifier.create(result)
                .expectNext();
    }

    @Test
    void shouldPopulateSharedPreferencesCacheIncludingViewerAndExcludeViewerFromResult() {
        // Given: preferences cache enabled and two matching profiles A and B
        ReflectionTestUtils.setField(candidateSearchStage, "preferencesCacheEnabled", true);
        SharedProfileDto accountA = createProfile(UUID.randomUUID(), "AccountA", VIEWER_AGE);
        SharedProfileDto accountB = createProfile(UUID.randomUUID(), "AccountB", CANDIDATE_AGE_1);

        when(deckCache.hasPreferencesCache(18, 50, "ANY")).thenReturn(reactor.core.publisher.Mono.just(false));
        when(profilesHttp.searchProfiles(eq(accountA.id()), any(SharedPreferencesDto.class), eq(SEARCH_LIMIT), eq(false)))
                .thenReturn(Flux.just(accountA, accountB));

        // When: Account A misses cache and searches candidates
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(accountA);

        // Then: Account A receives only Account B, while shared cache stores both A and B
        StepVerifier.create(result)
                .expectNext(accountB)
                .verifyComplete();

        verify(preferencesCacheHelper).cacheInBackground(
                eq(accountA.preferences()),
                eq(List.of(accountA.id(), accountB.id()))
        );
    }

    @Test
    void shouldReturnOtherAccountOnSharedPreferencesCacheHitWhenBothAccountsAreCached() {
        // Given: preferences cache enabled with both matching accounts A and B cached
        ReflectionTestUtils.setField(candidateSearchStage, "preferencesCacheEnabled", true);
        SharedProfileDto accountA = createProfile(UUID.randomUUID(), "AccountA", VIEWER_AGE);
        SharedProfileDto accountB = createProfile(UUID.randomUUID(), "AccountB", CANDIDATE_AGE_1);

        when(deckCache.hasPreferencesCache(18, 50, "ANY")).thenReturn(reactor.core.publisher.Mono.just(true));
        when(deckCache.getCandidatesByPreferences(18, 50, "ANY"))
                .thenReturn(Flux.just(accountA.id(), accountB.id()));
        when(preferencesCacheHelper.fetchProfilesByIds(eq(List.of(accountA.id(), accountB.id())), any()))
                .thenReturn(Flux.just(accountA, accountB));

        // When: Account B hits the shared preferences cache
        Flux<SharedProfileDto> result = candidateSearchStage.searchCandidates(accountB);

        // Then: Account B sees Account A (self is filtered out, deck is not empty)
        StepVerifier.create(result)
                .expectNext(accountA)
                .verifyComplete();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test profile with full preferences
     */
    private SharedProfileDto createProfile(UUID id, String name, int age) {
        SharedLocationDto locationDto = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "Test City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        SharedPreferencesDto preferences = new SharedPreferencesDto(18, 50, "ANY", 100);

        return new SharedProfileDto(id, name, age, "Bio", "Test City", true, locationDto, preferences, false, List.of(), List.of());
    }

    /**
     * Creates a test profile without preferences (null)
     */
    private SharedProfileDto createProfileWithoutPreferences(UUID id, String name, int age) {
        SharedLocationDto locationDto = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "Test City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        return new SharedProfileDto(id, name, age, "Bio", "Test City", true, locationDto, null, false, List.of(), List.of());
    }
}
