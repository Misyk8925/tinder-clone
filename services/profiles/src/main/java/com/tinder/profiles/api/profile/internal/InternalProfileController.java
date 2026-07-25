package com.tinder.profiles.api.profile.internal;
import com.tinder.profiles.infrastructure.persistence.profile.internal.InternalProfileService;

import com.tinder.profiles.api.profile.IdsQueryParamParser;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal API for profile operations.
 * Not exposed to external clients - only used by other microservices.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profiles/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final InternalProfileService profileService;
    private final IdsQueryParamParser idsQueryParamParser;
    private final com.tinder.profiles.infrastructure.persistence.profile.ProfileQueryService profileQueryService;

    /**
     * Resolves a Keycloak userId (JWT {@code sub}) to the active profileId.
     * Used by deck-read to key deck lookups by profileId. Backed by the
     * identity cache, so repeated lookups are cheap.
     */
    @GetMapping("/id-by-user/{userId}")
    public ResponseEntity<UUID> getProfileIdByUserId(@PathVariable String userId) {
        UUID profileId = profileQueryService.getActiveProfileIdByUserId(userId);
        if (profileId == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileId);
    }

    @GetMapping("/search")
    public ResponseEntity<List<SharedProfileDto>> search(
            @RequestParam UUID viewerId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) Integer maxRange,
            @RequestParam(defaultValue = "100") Integer limit) {

        // Validate limit
        if (limit < 1 || limit > 2000) {
            log.warn("Invalid limit parameter: {}. Must be between 1 and 2000", limit);
            return ResponseEntity.badRequest().build();
        }

        PreferencesDto prefs = new PreferencesDto(null, minAge, maxAge, gender, maxRange);
        List<SharedProfileDto> results = profileService.searchByViewerPrefs(viewerId, prefs, limit);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/by-ids")
    public ResponseEntity<List<SharedProfileDto>> getMany(@RequestParam String ids) {
        try {
            List<UUID> uuidList = idsQueryParamParser.parse(ids);
            List<SharedProfileDto> results = profileService.getMany(uuidList);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ids parameter for /internal/by-ids: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<SharedProfileDto> getOne(@PathVariable UUID id) {
        List<SharedProfileDto> results = profileService.getMany(List.of(id));
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(results.get(0));
    }


    @GetMapping("/active")
    public ResponseEntity<List<SharedProfileDto>> getActiveUsers() {
        List<SharedProfileDto> activeUsers = profileService.getActiveUsers();
        log.debug("Fetched {} active users", activeUsers.size());
        return ResponseEntity.ok(activeUsers);
    }

}
