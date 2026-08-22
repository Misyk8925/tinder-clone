package com.tinder.deckread.service;

import com.tinder.deckread.client.DeckCardProjectionBackfillClient;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelKeys;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.UUID;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * When the card catalog is empty, asks Profiles to enqueue a durable backfill.
 * Discover stays BUILDING until mappings exist; the HTTP path does not wait on Profiles.
 */
@ApplicationScoped
public class CatalogBackfillTrigger {

    private static final Logger LOG = Logger.getLogger(CatalogBackfillTrigger.class);

    private final ProfileProjectionStore profiles;
    private final ReactiveRedisDataSource redis;
    private final ReactiveValueCommands<String, String> values;
    private final DeckCardProjectionBackfillClient backfill;
    private final boolean enabled;

    @ConfigProperty(name = "deck-read.auto-backfill.scheduled", defaultValue = "true")
    boolean scheduledEnabled;

    @Inject
    public CatalogBackfillTrigger(
            ProfileProjectionStore profiles,
            @RedisClientName("read-model") ReactiveRedisDataSource redis,
            @RestClient DeckCardProjectionBackfillClient backfill,
            @ConfigProperty(name = "deck-read.auto-backfill-on-empty", defaultValue = "false") boolean enabled
    ) {
        this.profiles = profiles;
        this.redis = redis;
        this.values = redis.value(String.class);
        this.backfill = backfill;
        this.enabled = enabled;
    }

    @Scheduled(every = "${deck-read.auto-backfill.interval:30s}", concurrentExecution = SKIP)
    Uni<Void> scheduled() {
        if (!scheduledEnabled) {
            return Uni.createFrom().voidItem();
        }
        return requestIfEmpty();
    }

    public Uni<Void> requestIfEmpty() {
        if (!enabled) {
            return Uni.createFrom().voidItem();
        }
        return profiles.hasAnyCard()
                .flatMap(hasCards -> Boolean.TRUE.equals(hasCards) ? Uni.createFrom().voidItem() : startOrResume())
                .onFailure().invoke(failure -> LOG.warnf(failure, "Deck Card catalog backfill request failed"))
                .onFailure().recoverWithUni(ignored -> Uni.createFrom().voidItem());
    }

    public void requestIfEmptyAsync() {
        requestIfEmpty().subscribe().with(ignored -> { }, failure -> { });
    }

    private Uni<Void> startOrResume() {
        String generated = UUID.randomUUID().toString();
        return redis.execute("SET", ReadModelKeys.autoBackfillRun(), generated, "NX")
                .flatMap(ignored -> values.get(ReadModelKeys.autoBackfillRun()))
                .flatMap(runId -> {
                    if (runId == null || runId.isBlank()) {
                        return Uni.createFrom().voidItem();
                    }
                    LOG.infof("Requesting Profiles Deck Card catalog backfill runId=%s", runId);
                    return backfill.startOrResume(UUID.fromString(runId));
                });
    }
}
