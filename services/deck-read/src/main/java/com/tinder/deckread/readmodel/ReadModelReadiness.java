package com.tinder.deckread.readmodel;

import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Production recovery gate. The marker is set only after backfill/count/lag verification. */
@ApplicationScoped
public class ReadModelReadiness {

    public static final String READ_MODEL_NOT_READY = "READ_MODEL_NOT_READY";

    private final ReactiveValueCommands<String, String> values;

    @ConfigProperty(name = "deck-read.read-model.require-ready-marker", defaultValue = "false")
    boolean requireReadyMarker;

    @Inject
    public ReadModelReadiness(@RedisClientName("read-model") ReactiveRedisDataSource redis) {
        this.values = redis.value(String.class);
    }

    public Uni<Boolean> isReady() {
        if (!requireReadyMarker) {
            return Uni.createFrom().item(true);
        }
        return values.get(ReadModelKeys.readiness())
                .map("READY"::equals)
                .onFailure().recoverWithItem(false);
    }

    public Uni<Boolean> isRepeatReady() {
        if (!requireReadyMarker) {
            return Uni.createFrom().item(true);
        }
        return values.get(ReadModelKeys.repeatReadiness())
                .map("READY"::equals)
                .onFailure().recoverWithItem(false);
    }

    public Uni<Void> markReady() {
        return values.set(ReadModelKeys.readiness(), "READY");
    }

    public Uni<Void> markRepeatReady() {
        return values.set(ReadModelKeys.repeatReadiness(), "READY");
    }
}
