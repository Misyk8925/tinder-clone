package com.tinder.deckread.readmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.deckread.dto.DeckCardDto;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Version-gated profile cards and JWT-user to profile identity mapping. */
@ApplicationScoped
public class ProfileProjectionStore {

    private static final String APPLY_VERSION_SCRIPT = """
            local current = tonumber(redis.call('HGET', KEYS[1], 'version') or '-1')
            local incoming = tonumber(ARGV[1])
            if incoming < current then return -1 end
            if incoming == current then
              if redis.call('HGET', KEYS[1], 'eventId') == ARGV[2] then return 0 end
              return -2
            end
            redis.call('HSET', KEYS[1],
                'version', ARGV[1],
                'eventId', ARGV[2],
                'userId', ARGV[3],
                'operation', ARGV[4],
                'card', ARGV[5])
            return 1
            """;

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hashes;
    private final ReactiveValueCommands<String, String> values;
    private final ReactiveKeyCommands<String> keys;
    private final ObjectMapper mapper;

    @Inject
    public ProfileProjectionStore(
            @RedisClientName("read-model") ReactiveRedisDataSource redis,
            ObjectMapper mapper
    ) {
        this.redis = redis;
        this.hashes = redis.hash(String.class);
        this.values = redis.value(String.class);
        this.keys = redis.key();
        this.mapper = mapper;
    }

    public Uni<Void> apply(ProfileDeckCardProjectionEvent event) {
        DeckCardDto card = toCard(event);
        String cardJson = write(card);
        return redis.execute(
                        "EVAL", APPLY_VERSION_SCRIPT, "1", ReadModelKeys.profile(event.profileId()),
                        Long.toString(event.version()), event.eventId().toString(), event.userId(),
                        event.operation().name(), cardJson)
                .map(response -> response.toInteger())
                .flatMap(result -> {
                    // Older or conflicting same-version deliveries are complete
                    // no-ops. An exact duplicate (result 0) intentionally retries
                    // the cross-slot mapping write after a partial failure.
                    if (result < 0) {
                        return Uni.createFrom().voidItem();
                    }
                    String mappingKey = ReadModelKeys.userToProfile(event.userId());
                    if (event.operation() == ProfileProjectionOperation.DELETE) {
                        return keys.del(mappingKey).replaceWithVoid();
                    }
                    return values.set(mappingKey, event.profileId().toString());
                });
    }

    public Uni<UUID> viewerProfileId(String viewerUserId) {
        return values.get(ReadModelKeys.userToProfile(viewerUserId))
                .map(value -> value == null ? null : UUID.fromString(value));
    }

    public Uni<Optional<DeckCardDto>> card(UUID profileId) {
        return hashes.hgetall(ReadModelKeys.profile(profileId))
                .map(fields -> {
                    if (fields == null || fields.isEmpty()
                            || ProfileProjectionOperation.DELETE.name().equals(fields.get("operation"))) {
                        return Optional.empty();
                    }
                    DeckCardDto card = read(fields.get("card"));
                    return card.isActive() ? Optional.of(card) : Optional.empty();
                });
    }

    public Uni<Map<UUID, DeckCardDto>> cards(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        List<Uni<Map.Entry<UUID, Optional<DeckCardDto>>>> reads = profileIds.stream()
                .distinct()
                .map(id -> card(id).map(card -> Map.entry(id, card)))
                .toList();
        return Uni.combine().all().unis(reads).with(results -> {
            java.util.LinkedHashMap<UUID, DeckCardDto> cards = new java.util.LinkedHashMap<>();
            for (Object result : results) {
                @SuppressWarnings("unchecked")
                Map.Entry<UUID, Optional<DeckCardDto>> entry =
                        (Map.Entry<UUID, Optional<DeckCardDto>>) result;
                entry.getValue().ifPresent(card -> cards.put(entry.getKey(), card));
            }
            return Map.copyOf(cards);
        });
    }

    private DeckCardDto toCard(ProfileDeckCardProjectionEvent event) {
        var card = event.card();
        var preferences = card.preferences();
        return new DeckCardDto(
                card.profileId(), card.name(), card.age(), card.city(), card.bio(), card.isActive(),
                new DeckCardDto.Preferences(
                        preferences.minAge(), preferences.maxAge(), preferences.gender(),
                        preferences.maxDistanceKm()),
                card.photos().stream()
                        .map(photo -> new DeckCardDto.Photo(photo.photoId(), photo.url(), photo.order()))
                        .toList(),
                card.hobbies());
    }

    private String write(DeckCardDto card) {
        try {
            return mapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize Deck Card projection", e);
        }
    }

    private DeckCardDto read(String json) {
        try {
            return mapper.readValue(json, DeckCardDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize Deck Card projection", e);
        }
    }
}
