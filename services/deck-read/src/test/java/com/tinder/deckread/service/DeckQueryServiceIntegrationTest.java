package com.tinder.deckread.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.dto.DeckEntry;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.client.ProfilesClient;
import com.tinder.deckread.redis.DeckKeySchema;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for the read → ensure-on-miss → re-read → hydrate → reorder orchestration in
 * {@link DeckQueryService}. Uses a real Redis (Dev Services) for the reader and Mockito mocks for
 * the two REST clients.
 */
@QuarkusTest
class DeckQueryServiceIntegrationTest {

    @Inject
    DeckQueryService service;

    @Inject
    RedisDataSource redis;

    @Inject
    ReactiveRedisDataSource reactiveRedis;

    @InjectMock
    @RestClient
    DeckEnsureClient ensureClient;

    @InjectMock
    @RestClient
    ProfilesClient profilesClient;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- helpers -------------------------------------------------------------

    private String member(UUID profileId) {
        try {
            return mapper.writeValueAsString(new DeckEntry(profileId, false));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void seed(UUID viewer, double score, UUID profileId) {
        redis.sortedSet(String.class, String.class).zadd(DeckKeySchema.deck(viewer), score, member(profileId));
    }

    /** Reactive seed (safe to run on the event loop) used inside the ensure() mock answer. */
    private Uni<Boolean> seedReactivelyThenTrue(UUID viewer, double score, UUID profileId) {
        return reactiveRedis.sortedSet(String.class)
                .zadd(DeckKeySchema.deck(viewer), score, member(profileId))
                .replaceWith(true);
    }

    private SharedProfileDto profile(UUID id, boolean deleted) {
        return new SharedProfileDto(id, "name-" + id, 25, null, "Berlin", true, null,
                new SharedPreferencesDto(18, 99, "ALL", 50), deleted, List.of(), List.of());
    }

    private List<DeckCardDto> get(UUID viewer, int offset, int limit) {
        return service.getDeck(viewer, offset, limit).await().indefinitely();
    }

    // ---- tests ---------------------------------------------------------------

    @Test
    void presentDeckIsHydratedInDeckOrderWithoutEnsure() {
        UUID viewer = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        seed(viewer, 10.0, p1); // rank 1
        seed(viewer, 30.0, p2); // rank 0

        // Profiles service returns them in arbitrary order; service must reorder to deck order.
        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().item(List.of(profile(p1, false), profile(p2, false))));

        List<UUID> ids = get(viewer, 0, 10).stream().map(DeckCardDto::profileId).toList();

        assertThat(ids).containsExactly(p2, p1);
        verify(ensureClient, never()).ensure(any());
    }

    @Test
    void missingDeckTriggersEnsureThenRereads() {
        UUID viewer = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();

        // ensure() simulates the write service building the deck, then reports success.
        when(ensureClient.ensure(viewer)).thenReturn(seedReactivelyThenTrue(viewer, 5.0, p1));
        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().item(List.of(profile(p1, false))));

        List<UUID> ids = get(viewer, 0, 10).stream().map(DeckCardDto::profileId).toList();

        assertThat(ids).containsExactly(p1);
        verify(ensureClient).ensure(viewer);
    }

    @Test
    void ensureReturningFalseYieldsEmptyAndSkipsHydration() {
        UUID viewer = UUID.randomUUID();
        when(ensureClient.ensure(viewer)).thenReturn(Uni.createFrom().item(false));

        assertThat(get(viewer, 0, 10)).isEmpty();
        verify(profilesClient, never()).getByIds(anyString());
    }

    @Test
    void ensureFailureIsRecoveredAsEmpty() {
        UUID viewer = UUID.randomUUID();
        when(ensureClient.ensure(viewer))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("deck service down")));

        assertThat(get(viewer, 0, 10)).isEmpty();
        verify(profilesClient, never()).getByIds(anyString());
    }

    @Test
    void servesStaleProfilesWhenHydrationFails() {
        UUID viewer = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        seed(viewer, 10.0, p1);

        // First read succeeds and populates the cache.
        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().item(List.of(profile(p1, false))));
        assertThat(get(viewer, 0, 10).stream().map(DeckCardDto::profileId).toList()).containsExactly(p1);

        // Now profiles is "down". With the test TTL=0 the entry is stale, so the fetch is
        // attempted, fails, and the stale cached copy is served instead of erroring.
        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("profiles down")));
        assertThat(get(viewer, 0, 10).stream().map(DeckCardDto::profileId).toList()).containsExactly(p1);
    }

    @Test
    void hydrationFailureWithNoCachedCopyYieldsEmpty() {
        UUID viewer = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        seed(viewer, 10.0, p1);

        // Profiles down and nothing cached yet -> graceful empty, not a 5xx.
        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("profiles down")));
        assertThat(get(viewer, 0, 10)).isEmpty();
    }

    @Test
    void deletedProfilesAreDroppedFromHydratedResult() {
        UUID viewer = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        seed(viewer, 30.0, deleted); // rank 0, but soft-deleted
        seed(viewer, 10.0, alive);   // rank 1

        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().item(List.of(profile(alive, false), profile(deleted, true))));

        List<UUID> ids = get(viewer, 0, 10).stream().map(DeckCardDto::profileId).toList();

        assertThat(ids).containsExactly(alive);
    }
}
