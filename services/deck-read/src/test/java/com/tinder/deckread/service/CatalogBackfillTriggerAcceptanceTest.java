package com.tinder.deckread.service;

import com.tinder.deckread.client.DeckCardProjectionBackfillClient;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelKeys;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("acceptance")
@DisplayName("Feature: Deck Read backfills an empty card catalog from Profiles")
class CatalogBackfillTriggerAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given no projected cards, when Discover misses the viewer mapping, then Profiles backfill is started with a stable runId")
    void emptyCatalogPostsBackfillOnceWithStableRunId() {
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        ReactiveRedisDataSource redis = mock(ReactiveRedisDataSource.class);
        @SuppressWarnings("unchecked")
        ReactiveValueCommands<String, String> values = mock(ReactiveValueCommands.class);
        DeckCardProjectionBackfillClient client = mock(DeckCardProjectionBackfillClient.class);
        when(redis.value(String.class)).thenReturn(values);
        when(profiles.hasAnyCard()).thenReturn(Uni.createFrom().item(false));
        UUID runId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(redis.execute(eq("SET"), eq(ReadModelKeys.autoBackfillRun()), any(), eq("NX")))
                .thenReturn(Uni.createFrom().item(mock(Response.class)));
        when(values.get(ReadModelKeys.autoBackfillRun())).thenReturn(Uni.createFrom().item(runId.toString()));
        when(client.startOrResume(runId)).thenReturn(Uni.createFrom().voidItem());

        CatalogBackfillTrigger trigger = new CatalogBackfillTrigger(profiles, redis, client, true);
        trigger.requestIfEmpty().await().indefinitely();
        trigger.requestIfEmpty().await().indefinitely();

        verify(client, times(2)).startOrResume(runId);
    }

    @Test
    @DisplayName("Scenario: Given auto-backfill disabled, when emptiness is checked, then Profiles is not called")
    void disabledTriggerDoesNotPostBackfill() {
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        ReactiveRedisDataSource redis = mock(ReactiveRedisDataSource.class);
        DeckCardProjectionBackfillClient client = mock(DeckCardProjectionBackfillClient.class);
        when(redis.value(String.class)).thenReturn(mock(ReactiveValueCommands.class));

        CatalogBackfillTrigger trigger = new CatalogBackfillTrigger(profiles, redis, client, false);
        trigger.requestIfEmpty().await().indefinitely();

        verifyNoInteractions(client);
        verifyNoInteractions(profiles);
    }

    @Test
    @DisplayName("Scenario: Given projected cards already exist, when emptiness is checked, then Profiles is not called")
    void populatedCatalogDoesNotPostBackfill() {
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        ReactiveRedisDataSource redis = mock(ReactiveRedisDataSource.class);
        DeckCardProjectionBackfillClient client = mock(DeckCardProjectionBackfillClient.class);
        when(redis.value(String.class)).thenReturn(mock(ReactiveValueCommands.class));
        when(profiles.hasAnyCard()).thenReturn(Uni.createFrom().item(true));

        CatalogBackfillTrigger trigger = new CatalogBackfillTrigger(profiles, redis, client, true);
        trigger.requestIfEmpty().await().indefinitely();

        verifyNoInteractions(client);
    }
}
