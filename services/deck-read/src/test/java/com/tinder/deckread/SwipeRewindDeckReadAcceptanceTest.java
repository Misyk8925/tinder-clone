package com.tinder.deckread;

import com.tinder.contracts.event.v1.SwipeRevokedEventV1;
import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.DeckMaterializationRequest;
import com.tinder.deckread.messaging.DeckMaterializationRequester;
import com.tinder.deckread.messaging.DeckReadEventMaterializers;
import com.tinder.deckread.messaging.MaterializationReason;
import com.tinder.deckread.readmodel.DeckMaterializationRequestStore;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelKeys;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.redis.DeckRedisReader;
import com.tinder.deckread.redis.SourceDeckSnapshot;
import com.tinder.deckread.service.DeckMaterializationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deck Read swipe-revoked projection and position-0 materialization acceptance for FR-4, FR-8, FR-9, NFR-3. */
@Tag("acceptance")
@DisplayName("Feature: Deck Read clears swipe exclusion and materializes rewound profile at position 0 (FR-4, FR-8, FR-9)")
class SwipeRewindDeckReadAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given swipe-revoked Kafka contract, when Deck Read worker configures channels, then swipe-revoked incoming channel is wired")
    void deckReadConfiguresSwipeRevokedChannel() throws Exception {
        // Given
        String config = Files.readString(Path.of("src/main/resources/application.properties"));

        // When / Then
        assertThat(config).contains("mp.messaging.incoming.swipe-revoked.topic=swipe-revoked");
        assertThat(config).contains("mp.messaging.incoming.swipe-revoked.dead-letter-queue.topic=swipe-revoked.dlt");
    }

    @Test
    @DisplayName("Scenario: Given a viewer profileId, when ReadModelKeys.rewound is built, then it shares the viewer Redis Cluster hash tag")
    void rewoundKeySharesViewerClusterHashTag() {
        // Given
        UUID viewerId = UUID.randomUUID();

        // When
        String key = ReadModelKeys.rewound(viewerId);

        // Then
        assertThat(key).isEqualTo("dr:viewer:{" + viewerId + "}:rewound");
    }

    @Test
    @DisplayName("Scenario: Given a SwipeRevokedEventV1 arrives, when DeckReadEventMaterializers handles it, then ViewerMutationStore.applySwipeRevoked runs and SWIPE_REVOKED materialization is requested")
    void materializerAppliesSwipeRevokedAndRequestsRebuild() throws Exception {
        // Given
        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        DeckMaterializationRequester requester = mock(DeckMaterializationRequester.class);
        SwipeRevokedEventV1 event = new SwipeRevokedEventV1(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                viewerId.toString(),
                targetId.toString(),
                false,
                1_795_643_650_000L,
                1_795_643_712_410L
        );
        when(mutations.applySwipeRevoked(event)).thenReturn(Uni.createFrom().voidItem());
        when(requester.request(viewerId, MaterializationReason.SWIPE_REVOKED)).thenReturn(Uni.createFrom().voidItem());

        DeckReadEventMaterializers materializers = new DeckReadEventMaterializers();
        var mutField = DeckReadEventMaterializers.class.getDeclaredField("viewerMutations");
        mutField.setAccessible(true);
        mutField.set(materializers, mutations);
        var reqField = DeckReadEventMaterializers.class.getDeclaredField("requester");
        reqField.setAccessible(true);
        reqField.set(materializers, requester);

        // When
        materializers.onSwipeRevoked(event).await().indefinitely();

        // Then
        verify(mutations).applySwipeRevoked(event);
        verify(requester).request(viewerId, MaterializationReason.SWIPE_REVOKED);
    }

    @Test
    @DisplayName("Scenario: Given a rewound profile is not yet in Deck Redis source (FR-8), when DeckMaterializationService materializes, then the rewound card is placed at position 0 ahead of existing fresh cards")
    void deckMaterializationPlacesRewoundCardAtPositionZero() throws Exception {
        // Given
        UUID viewer = UUID.randomUUID();
        UUID rewoundId = UUID.randomUUID();
        UUID existingFreshId = UUID.randomUUID();
        DeckCardDto rewoundCard = card(rewoundId);
        DeckCardDto existingFreshCard = card(existingFreshId);

        DeckMaterializationRequestStore requests = mock(DeckMaterializationRequestStore.class);
        DeckSnapshotStore locks = mock(DeckSnapshotStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        DeckEnsureClient deckEnsure = mock(DeckEnsureClient.class);
        DeckRedisReader source = mock(DeckRedisReader.class);
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);

        when(requests.requestedRevision(viewer)).thenReturn(Uni.createFrom().item(1L));
        when(materialized.meta(viewer)).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(locks.acquireBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().item(true));
        when(locks.renewBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().item(true));
        when(locks.releaseBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(deckEnsure.ensure(viewer)).thenReturn(Uni.createFrom().item(true));
        when(source.readStable(viewer, MaterializedDeckStore.TOTAL_WINDOW))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(List.of(existingFreshId), "100")));
        when(mutations.rewound(viewer)).thenReturn(Uni.createFrom().item(Optional.of(rewoundId)));
        List<UUID> expectedOrder = List.of(rewoundId, existingFreshId);
        when(profiles.cards(expectedOrder))
                .thenReturn(Uni.createFrom().item(Map.of(rewoundId, rewoundCard, existingFreshId, existingFreshCard)));
        when(mutations.swiped(viewer, expectedOrder)).thenReturn(Uni.createFrom().item(Set.of()));
        when(mutations.matched(viewer, expectedOrder)).thenReturn(Uni.createFrom().item(Set.of()));
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(false));
        when(materialized.install(
                eq(viewer), eq(1L), eq(List.of(rewoundCard, existingFreshCard)), eq(2),
                eq(DeckState.READY), eq("100"), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        DeckMaterializationService service = new DeckMaterializationService(new SimpleMeterRegistry());
        setField(service, "requests", requests);
        setField(service, "locks", locks);
        setField(service, "materialized", materialized);
        setField(service, "deckEnsure", deckEnsure);
        setField(service, "source", source);
        setField(service, "profiles", profiles);
        setField(service, "mutations", mutations);
        setField(service, "readiness", readiness);

        // When
        service.materialize(new DeckMaterializationRequest(
                UUID.randomUUID(), viewer, 1L, MaterializationReason.SWIPE_REVOKED, "", Instant.now()))
                .await().indefinitely();

        // Then
        verify(materialized).install(
                eq(viewer), eq(1L), eq(List.of(rewoundCard, existingFreshCard)), eq(2),
                eq(DeckState.READY), eq("100"), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given a delayed SwipeSaved arrives after SwipeRevoked (RISK-3 regression), when APPLY_FIRST_SWIPE_SCRIPT evaluates, then rewound timestamp guard runs before HSETNX")
    void delayedSwipeSavedGuardRunsBeforeHsetnx() throws Exception {
        // Given
        String storeSource = Files.readString(Path.of("src/main/java/com/tinder/deckread/readmodel/ViewerMutationStore.java"));
        int rewoundCheck = storeSource.indexOf("if swipeTs < rewoundAt then");
        int hsetnx = storeSource.indexOf("redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2])");

        // When / Then
        assertThat(rewoundCheck).isGreaterThan(0);
        assertThat(hsetnx).isGreaterThan(rewoundCheck);
    }

    @Test
    @DisplayName("Scenario: Given a SwipeRevokedEventV1 fails into DLT (NFR-5), when SanitizedDeckReadDltSerializer runs, then replay UUIDs are preserved with zero PII")
    void sanitizedDltSerializerPreservesSwipeRevokedReplayIdentifiers() {
        // Given
        var serializer = new com.tinder.deckread.messaging.SanitizedDeckReadDltSerializer();
        String eventId = UUID.randomUUID().toString();
        String revokedId = UUID.randomUUID().toString();
        String viewerId = UUID.randomUUID().toString();
        String targetId = UUID.randomUUID().toString();
        var event = new SwipeRevokedEventV1(eventId, revokedId, viewerId, targetId, false, 1_795_643_650_000L, 1_795_643_712_410L);

        // When
        String json = new String(serializer.serialize("swipe-revoked.dlt", event), java.nio.charset.StandardCharsets.UTF_8);

        // Then
        assertThat(json)
                .contains("\"payloadType\":\"swipe.revoked\"")
                .contains(eventId)
                .contains(revokedId)
                .contains(viewerId)
                .contains(targetId);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static DeckCardDto card(UUID id) {
        return new DeckCardDto(
                id, "Name", 28, "Vienna", "Bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50),
                List.of(), List.of());
    }
}
