package com.example.swipes_demo.profileCache.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Synchronous fallback client for the profiles service.
 *
 * <p>Used when a profile is not found in the local cache (Redis + DB) —
 * typically right after registration, before the Kafka {@code profile.created}
 * event has been consumed and the local cache has been populated.
 *
 * <p>On error (profiles service unavailable) returns an empty set,
 * which means the calling code treats unverified profiles as non-existent
 * (fail-closed behaviour).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceClient {

    private final WebClient profilesWebClient;

    /**
     * Returns the subset of {@code profileIds} that actually exist in the profiles service.
     * An empty set is returned if the remote call fails, so the caller can react accordingly.
     */
    public Mono<Set<UUID>> findExisting(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Mono.just(Set.of());
        }

        String ids = profileIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        return profilesWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/by-ids")
                        .queryParam("ids", ids)
                        .build())
                .retrieve()
                .bodyToFlux(ProfileExistsDto.class)
                .map(ProfileExistsDto::profileId)
                .collect(Collectors.toSet())
                .doOnSuccess(found -> log.debug(
                        "Profiles service confirmed {}/{} profiles exist",
                        found.size(), profileIds.size()))
                .doOnError(e -> log.warn(
                        "Profiles service fallback failed for ids [{}]: {}",
                        ids, e.getMessage()))
                .onErrorReturn(Set.of());
    }
}
