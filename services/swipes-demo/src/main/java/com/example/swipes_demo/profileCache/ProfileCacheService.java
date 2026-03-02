package com.example.swipes_demo.profileCache;

import com.example.swipes_demo.profileCache.kafka.ProfileCreateEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCacheService {

    private static final String PROFILE_EXISTS_SET_KEY = "profiles:exists";

    private final ProfileCacheRepository profileCacheRepository;
    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

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

    public Mono<Boolean> existsAll(UUID firstProfileId, UUID secondProfileId) {
        if (firstProfileId.equals(secondProfileId)) {
            return Mono.just(false);
        }

        String firstId = firstProfileId.toString();
        String secondId = secondProfileId.toString();

        Mono<Boolean> firstExists = isInRedis(firstId);
        Mono<Boolean> secondExists = isInRedis(secondId);

        return Mono.zip(firstExists, secondExists)
                .flatMap(tuple -> {
                    boolean foundInRedis = tuple.getT1() && tuple.getT2();
                    if (foundInRedis) {
                        return Mono.just(true);
                    }

                    return Mono.fromCallable(() ->
                                    profileCacheRepository.countByProfileIdIn(List.of(firstProfileId, secondProfileId)) == 2
                            )
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(existsInDb -> {
                                if (!existsInDb) {
                                    return Mono.just(false);
                                }

                                return reactiveStringRedisTemplate.opsForSet()
                                        .add(PROFILE_EXISTS_SET_KEY, firstId, secondId)
                                        .onErrorResume(error -> Mono.just(0L))
                                        .thenReturn(true);
                            });
                });
    }

    private Mono<Boolean> isInRedis(String profileId) {
        return reactiveStringRedisTemplate.opsForSet()
                .isMember(PROFILE_EXISTS_SET_KEY, profileId)
                .map(Boolean.TRUE::equals)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
}
