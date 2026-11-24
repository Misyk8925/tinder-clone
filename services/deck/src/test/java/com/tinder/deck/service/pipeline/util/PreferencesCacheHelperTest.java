package com.tinder.deck.service.pipeline.util;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.service.DeckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferencesCacheHelperTest {

    @Mock
    private DeckCache deckCache;

    @Mock
    private ProfilesHttp profilesHttp;

    private PreferencesCacheHelper helper;

    @BeforeEach
    void setUp() {
        helper = new PreferencesCacheHelper(deckCache, profilesHttp);
    }

    @Test
    @DisplayName("Batches /by-ids calls to at most 100 IDs per request")
    void batchesProfilesLookupToMax100IdsPerCall() {
        List<UUID> ids = IntStream.range(0, 125).mapToObj(i -> UUID.randomUUID()).toList();
        when(profilesHttp.getProfilesByIds(anyList())).thenAnswer(invocation -> {
            List<UUID> batch = invocation.getArgument(0);
            return Flux.fromIterable(batch.stream().map(this::profile).toList());
        });

        StepVerifier.create(helper.fetchProfilesByIds(ids, Flux.empty()))
                .expectNextCount(125)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(profilesHttp, times(2)).getProfilesByIds(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues()).extracting(List::size).containsExactly(100, 25);
    }

    @Test
    @DisplayName("Falls back cleanly without emitting partial batch duplicates if a later /by-ids batch fails")
    void fallsBackWithoutDuplicatesWhenSecondBatchFails() {
        List<UUID> ids = IntStream.range(0, 125).mapToObj(i -> UUID.randomUUID()).toList();
        SharedProfileDto fallbackProfile = profile(UUID.randomUUID());

        when(profilesHttp.getProfilesByIds(anyList())).thenAnswer(invocation -> {
            List<UUID> batch = invocation.getArgument(0);
            if (batch.size() == 100) {
                return Flux.fromIterable(batch.stream().map(this::profile).toList());
            }
            return Flux.error(new RuntimeException("Second batch failed"));
        });

        StepVerifier.create(helper.fetchProfilesByIds(ids, Flux.just(fallbackProfile)))
                .expectNext(fallbackProfile)
                .verifyComplete();
    }

    private SharedProfileDto profile(UUID id) {
        return new SharedProfileDto(
                id,
                "User",
                25,
                "Bio",
                "City",
                true,
                new SharedLocationDto(UUID.randomUUID(), 0.0, 0.0, "City", LocalDateTime.now(), LocalDateTime.now()),
                new SharedPreferencesDto(18, 50, "all", 100),
                false,
                List.of(),
                List.of()
        );
    }
}
