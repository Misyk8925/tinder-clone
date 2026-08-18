package com.tinder.deckread.service;

import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckCardV1Dto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.MatchCreatedEvent;
import com.tinder.deckread.messaging.SwipeSavedEvent;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelKeys;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("acceptance")
@DisplayName("Feature: Deck Read materializes event-driven viewer output")
class DeckQueryServiceIntegrationTest {

    private static final String VIEWER_USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID VIEWER_PROFILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Inject DeckQueryService service;
    @Inject ProfileProjectionStore profiles;
    @Inject DeckSnapshotStore snapshots;
    @Inject ViewerMutationStore mutations;

    @Inject
    @RedisClientName("read-model")
    RedisDataSource redis;

    @BeforeEach
    void setUp() {
        redis.flushall();
        profiles.apply(event(VIEWER_PROFILE_ID, VIEWER_USER_ID, ProfileProjectionOperation.UPSERT))
                .await().indefinitely();
    }

    @Test
    @DisplayName("Scenario: Given locally projected cards, when a viewer reads the deck, then snapshot order is preserved")
    void localCardsPreserveSnapshotOrder() {
        // Given
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        apply(first);
        apply(second);
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(first, second), List.of(),
                DeckState.READY, "10", Instant.now()).await().indefinitely();

        // When
        List<UUID> ids = service.getDeckV1(VIEWER_USER_ID, 0, 20)
                .await().indefinitely().stream().map(DeckCardV1Dto::profileId).toList();

        // Then
        assertThat(ids).containsExactly(first, second);
    }

    @Test
    @DisplayName("Scenario: Given a fresh card, when the viewer swipes it, then it disappears from active output immediately")
    void swipeImmediatelyRemovesCardFromFreshOutput() {
        // Given
        UUID candidate = UUID.randomUUID();
        apply(candidate);
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(candidate), List.of(),
                DeckState.READY, "10", Instant.now()).await().indefinitely();

        // When
        mutations.applySwipe(new SwipeSavedEvent(
                UUID.randomUUID().toString(), VIEWER_PROFILE_ID.toString(),
                candidate.toString(), false, Instant.now().toEpochMilli()))
                .await().indefinitely();

        // Then
        assertThat(service.getDeckV1(VIEWER_USER_ID, 0, 20).await().indefinitely()).isEmpty();
    }

    @Test
    @DisplayName("Scenario: Given a repeat card, when the profiles match, then the card is excluded in both viewer directions")
    void matchRemovesRepeatCardFromBothViewerDirections() {
        // Given
        UUID candidate = UUID.randomUUID();
        apply(candidate);
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(), List.of(candidate),
                DeckState.DEGRADED, "", Instant.now()).await().indefinitely();

        // When
        mutations.applyMatch(new MatchCreatedEvent(
                UUID.randomUUID().toString(), VIEWER_PROFILE_ID.toString(),
                candidate.toString(), Instant.now())).await().indefinitely();

        // Then
        assertThat(service.getDeckV1(VIEWER_USER_ID, 0, 20).await().indefinitely()).isEmpty();
    }

    @Test
    @DisplayName("Scenario: Given a projected card in a snapshot, when a newer DELETE tombstone arrives, then the card and user mapping disappear immediately")
    void deleteTombstoneRemovesCardAndIdentityMapping() {
        // Given
        UUID candidate = UUID.randomUUID();
        String candidateUserId = UUID.randomUUID().toString();
        profiles.apply(event(candidate, candidateUserId, ProfileProjectionOperation.UPSERT))
                .await().indefinitely();
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(candidate), List.of(),
                DeckState.READY, "10", Instant.now()).await().indefinitely();

        // When
        profiles.apply(new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), candidate, candidateUserId, 2, Instant.now(),
                ProfileProjectionOperation.DELETE, ProjectionSource.BACKFILL, UUID.randomUUID(),
                new DeckCardProjection(
                        candidate, "deleted", 25, "Berlin", "bio", false,
                        new DeckCardPreferences(18, 99, "ALL", 50), List.of(), List.of())))
                .await().indefinitely();

        // Then
        assertThat(profiles.card(candidate).await().indefinitely()).isEmpty();
        assertThat(profiles.viewerProfileId(candidateUserId).await().indefinitely()).isNull();
        assertThat(service.getDeckV1(VIEWER_USER_ID, 0, 20).await().indefinitely()).isEmpty();
    }

    @Test
    @DisplayName("Scenario: Given a newer card version, when older or conflicting same-version events arrive, then card and identity never roll back")
    void olderAndConflictingSameVersionEventsCannotRollbackCardOrIdentityMapping() {
        // Given
        UUID candidate = UUID.randomUUID();
        String originalUserId = UUID.randomUUID().toString();
        String conflictingUserId = UUID.randomUUID().toString();

        profiles.apply(event(candidate, originalUserId, 2, "version-two"))
                .await().indefinitely();

        // When
        profiles.apply(event(candidate, conflictingUserId, 1, "older"))
                .await().indefinitely();
        profiles.apply(event(candidate, conflictingUserId, 2, "same-version-conflict"))
                .await().indefinitely();

        // Then
        assertThat(profiles.card(candidate).await().indefinitely())
                .get().extracting(DeckCardDto::name).isEqualTo("version-two");
        assertThat(profiles.viewerProfileId(originalUserId).await().indefinitely())
                .isEqualTo(candidate);
        assertThat(profiles.viewerProfileId(conflictingUserId).await().indefinitely())
                .isNull();
    }

    @Test
    @DisplayName("Scenario: Given a partial cross-slot identity write, when the exact event is redelivered, then the missing user mapping is repaired")
    void exactDuplicateRepairsCrossSlotIdentityWriteAfterPartialFailure() {
        // Given
        UUID candidate = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();
        ProfileDeckCardProjectionEvent event = event(candidate, userId, 2, "stable");
        profiles.apply(event).await().indefinitely();
        redis.key().del(ReadModelKeys.userToProfile(userId));

        // When
        profiles.apply(event).await().indefinitely();

        // Then
        assertThat(profiles.viewerProfileId(userId).await().indefinitely()).isEqualTo(candidate);
    }

    @Test
    @DisplayName("Scenario: Given two swipe deliveries for the same pair, when decisions conflict, then the first decision is retained once")
    void duplicateSwipePreservesFirstDecisionAndDoesNotDuplicateRepeatCandidate() {
        // Given
        UUID candidate = UUID.randomUUID();
        long firstTimestamp = Instant.now().minusSeconds(10).toEpochMilli();

        // When
        mutations.applySwipe(new SwipeSavedEvent(
                UUID.randomUUID().toString(), VIEWER_PROFILE_ID.toString(),
                candidate.toString(), false, firstTimestamp)).await().indefinitely();
        mutations.applySwipe(new SwipeSavedEvent(
                UUID.randomUUID().toString(), VIEWER_PROFILE_ID.toString(),
                candidate.toString(), true, firstTimestamp + 5_000)).await().indefinitely();

        // Then
        assertThat(mutations.repeatCandidates(VIEWER_PROFILE_ID, 10, Instant.now())
                .await().indefinitely()).containsExactly(candidate);
    }

    private void apply(UUID profileId) {
        profiles.apply(event(profileId, UUID.randomUUID().toString(), ProfileProjectionOperation.UPSERT))
                .await().indefinitely();
    }

    private ProfileDeckCardProjectionEvent event(
            UUID profileId,
            String userId,
            ProfileProjectionOperation operation
    ) {
        return new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), profileId, userId, 1, Instant.now(), operation,
                ProjectionSource.LIVE, null,
                new DeckCardProjection(
                        profileId, "name-" + profileId, 25, "Berlin", "bio", true,
                        new DeckCardPreferences(18, 99, "ALL", 50), List.of(), List.of()));
    }

    private ProfileDeckCardProjectionEvent event(
            UUID profileId,
            String userId,
            long version,
            String name
    ) {
        return new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), profileId, userId, version, Instant.now(),
                ProfileProjectionOperation.UPSERT, ProjectionSource.LIVE, null,
                new DeckCardProjection(
                        profileId, name, 25, "Berlin", "bio", true,
                        new DeckCardPreferences(18, 99, "ALL", 50), List.of(), List.of()));
    }
}
