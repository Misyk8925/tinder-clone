package com.example.swipes_demo.profileCache;

import com.example.swipes_demo.profileCache.client.ProfileServiceClient;
import com.example.swipes_demo.profileCache.kafka.ProfileCreateEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCacheService {

    private static final String PROFILE_EXISTS_SET_KEY = "profiles:exists";

    private final ProfileCacheRepository profileCacheRepository;
    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final ProfileServiceClient profileServiceClient;

    @Transactional
    public void saveProfileCache(ProfileCreateEvent event) {
        UUID profileId = event.getProfileId();
        if (profileId == null) {
            log.warn("Skipping ProfileCreateEvent with null profileId: {}", event);
            return;
        }

        Instant createdAt = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        String fallbackUserId = event.getUserId() != null ? event.getUserId() : "unknown";

        profileCacheRepository.findById(profileId).ifPresentOrElse(existing -> {
                    existing.setCreatedAt(createdAt);
                    if (event.getUserId() != null) {
                        existing.setUserId(event.getUserId());
                    }
                    profileCacheRepository.save(existing);
                    log.debug("Updated profile cache for profileId: {}", profileId);
                },
                () -> {
                    ProfileCache profileCacheModel = ProfileCache.builder()
                            .profileId(profileId)
                            .createdAt(createdAt)
                            .userId(fallbackUserId)
                            .build();
                    profileCacheRepository.save(profileCacheModel);
                    log.info("Saved profile cache for profileId: {}", profileId);
                });

        reactiveStringRedisTemplate.opsForSet()
                .add(PROFILE_EXISTS_SET_KEY, profileId.toString())
                .doOnError(error -> log.warn("Failed to write profile id {} to Redis cache", profileId, error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    @Transactional
    public void deleteProfileCache(ProfileDeleteEvent event) {
        UUID profileId = event.getProfileId();
        log.info("Deleting profile cache for profileId: {}", profileId);

        profileCacheRepository.findById(profileId).ifPresentOrElse(
                profile -> {
                    profileCacheRepository.delete(profile);
                    log.info("Successfully deleted profile cache for profileId: {}", profileId);
                },
                () -> log.warn("Profile cache not found for profileId: {}", profileId)
        );

        reactiveStringRedisTemplate.opsForSet()
                .remove(PROFILE_EXISTS_SET_KEY, profileId.toString())
                .doOnError(error -> log.warn("Failed to evict profile id {} from Redis cache", profileId, error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    /**
     * Checks that both profiles exist, going through three layers:
     * <ol>
     *   <li>Redis set (fast, in-memory)</li>
     *   <li>Local PostgreSQL cache (populated by Kafka events)</li>
     *   <li>Profiles service via HTTP (sync fallback for the Kafka lag window)</li>
     * </ol>
     * The sync fallback handles the race condition where a newly created profile
     * has not yet been reflected in the local cache because the
     * {@code profile.created} Kafka event has not been consumed yet.
     */
    public Mono<Boolean> existsAll(UUID firstProfileId, UUID secondProfileId) {
        return existsAll(firstProfileId, secondProfileId, null);
    }

    public Mono<Boolean> existsAll(UUID firstProfileId, UUID secondProfileId, String bearerToken) {
        if (firstProfileId.equals(secondProfileId)) {
            return Mono.just(false);
        }

        return Mono.zip(
                        isInRedis(firstProfileId.toString()),
                        isInRedis(secondProfileId.toString())
                )
                .flatMap(tuple -> {
                    boolean p1InRedis = tuple.getT1();
                    boolean p2InRedis = tuple.getT2();

                    // Layer 1: both hit Redis — no further I/O needed
                    if (p1InRedis && p2InRedis) {
                        return Mono.just(true);
                    }

                    // Collect IDs that missed Redis
                    List<UUID> notInRedis = new ArrayList<>();
                    if (!p1InRedis) notInRedis.add(firstProfileId);
                    if (!p2InRedis) notInRedis.add(secondProfileId);

                    // Layer 2: check local DB for the Redis misses
                    return checkInDb(notInRedis)
                            .flatMap(foundInDb -> {
                                Mono<Void> warmRedis = warmUpRedis(foundInDb);

                                List<UUID> notInDb = notInRedis.stream()
                                        .filter(id -> !foundInDb.contains(id))
                                        .toList();

                                // All accounted for via Redis + DB
                                if (notInDb.isEmpty()) {
                                    return warmRedis.thenReturn(true);
                                }

                                // Layer 3: sync fallback to profiles service.
                                // Covers the Kafka replication-lag window for brand-new profiles.
                                log.debug("Cache miss for {} profile(s), falling back to profiles service",
                                        notInDb.size());

                                return warmRedis.then(
                                        profileServiceClient.findExisting(notInDb, bearerToken)
                                                .flatMap(confirmedIds -> {
                                                    if (!confirmedIds.containsAll(notInDb)) {
                                                        log.debug("{} profile(s) not confirmed by profiles service",
                                                                notInDb.stream()
                                                                        .filter(id -> !confirmedIds.contains(id))
                                                                        .count());
                                                        return Mono.just(false);
                                                    }
                                                    // Confirmed — populate local cache so the next
                                                    // request is served from Redis/DB directly
                                                    return populateCache(notInDb).thenReturn(true);
                                                })
                                );
                            });
                });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Mono<Boolean> isInRedis(String profileId) {
        return reactiveStringRedisTemplate.opsForSet()
                .isMember(PROFILE_EXISTS_SET_KEY, profileId)
                .map(Boolean.TRUE::equals)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /** Returns the subset of {@code ids} that are present in the local DB cache. */
    private Mono<Set<UUID>> checkInDb(List<UUID> ids) {
        return Mono.fromCallable(() ->
                        profileCacheRepository.findAllById(ids)
                                .stream()
                                .map(ProfileCache::getProfileId)
                                .collect(Collectors.toSet())
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Adds the given profile IDs to the Redis set. Swallows Redis errors. */
    private Mono<Void> warmUpRedis(Set<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Mono.empty();
        }
        String[] ids = profileIds.stream().map(UUID::toString).toArray(String[]::new);
        return reactiveStringRedisTemplate.opsForSet()
                .add(PROFILE_EXISTS_SET_KEY, ids)
                .onErrorResume(e -> {
                    log.warn("Failed to warm Redis for {} profile(s)", profileIds.size(), e);
                    return Mono.just(0L);
                })
                .then();
    }

    /**
     * Persists profiles confirmed by the remote fallback into DB + Redis so
     * subsequent requests are served from the local cache.
     * Uses {@code "unknown"} as userId placeholder — the existence-check DTO
     * does not carry the full profile data. The placeholder will be overwritten
     * when the Kafka {@code profile.created} event is eventually consumed.
     */
    private Mono<Void> populateCache(List<UUID> profileIds) {
        return Mono.fromCallable(() -> {
                    profileIds.forEach(id ->
                            profileCacheRepository.findById(id).ifPresentOrElse(
                                    existing -> log.debug("Profile {} already in local cache, skipping insert", id),
                                    () -> profileCacheRepository.save(
                                            ProfileCache.builder()
                                                    .profileId(id)
                                                    .userId("unknown")
                                                    .createdAt(Instant.now())
                                                    .build()
                                    )
                            )
                    );
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(warmUpRedis(new HashSet<>(profileIds)));
    }
}
