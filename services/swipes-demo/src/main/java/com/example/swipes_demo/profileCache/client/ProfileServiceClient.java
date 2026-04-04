package com.example.swipes_demo.profileCache.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        return findExisting(profileIds, null);
    }

    public Mono<Set<UUID>> findExisting(List<UUID> profileIds, String bearerToken) {
        if (profileIds.isEmpty()) {
            return Mono.just(Set.of());
        }

        String ids = profileIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String jwtFingerprint = tokenFingerprint(bearerToken);
        log.info("Profiles fallback request endpoint=/by-ids idsCount={} jwtFingerprint={} hasAuthHeader={}",
                profileIds.size(), jwtFingerprint, bearerToken != null && !bearerToken.isBlank());

        return profilesWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/by-ids")
                        .queryParam("ids", ids)
                        .build())
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
                    }
                })
                .retrieve()
                .bodyToFlux(ProfileExistsDto.class)
                .map(ProfileExistsDto::profileId)
                .collect(Collectors.toSet())
                .doOnSuccess(found -> log.debug(
                        "Profiles service confirmed {}/{} profiles exist",
                        found.size(), profileIds.size()))
                .doOnError(e -> log.warn(
                        "Profiles service fallback failed for ids [{}], jwtFingerprint={}, hasAuthHeader={}: {}",
                        ids, jwtFingerprint, bearerToken != null && !bearerToken.isBlank(), e.getMessage()))
                .onErrorReturn(Set.of());
    }

    private String tokenFingerprint(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return "absent";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bearerToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return "sha256:" + sb;
        } catch (NoSuchAlgorithmException ex) {
            return "sha256-unavailable";
        }
    }
}
