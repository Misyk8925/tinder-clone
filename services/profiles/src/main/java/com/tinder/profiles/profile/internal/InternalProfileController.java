package com.tinder.profiles.profile.internal;


import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final InternalProfileService profileService;
    private final DeckService deckService;

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


    @GetMapping("/page")
    public ResponseEntity<List<SharedProfileDto>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Validate pagination parameters
        if (page < 0 || size < 1 || size > 100) {
            log.warn("Invalid pagination parameters: page={}, size={}. Size must be 1-100", page, size);
            return ResponseEntity.badRequest().build();
        }

        List<SharedProfileDto> results = profileService.fetchPage(page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<SharedProfileDto>> getMany(@RequestParam String ids) {
        log.info(">>> /by-ids called with raw parameter: [{}]", ids);
        log.info(">>> Parameter length: {}", ids.length());

        if (ids.isBlank()) {
            log.warn("Empty ids parameter provided to /by-ids");
            return ResponseEntity.badRequest().build();
        }

        // Parse comma-separated UUID string
        List<UUID> uuidList;
        try {
            String[] parts = ids.split(",");
            log.info(">>> Split into {} parts", parts.length);
            for (int i = 0; i < Math.min(3, parts.length); i++) {
                log.info(">>> Part[{}]: [{}]", i, parts[i]);
            }

            uuidList = java.util.Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        log.debug(">>> Parsing UUID: [{}]", s);
                        return UUID.fromString(s);
                    })
                    .toList();

            log.info(">>> Successfully parsed {} UUIDs", uuidList.size());
            for (int i = 0; i < Math.min(3, uuidList.size()); i++) {
                log.info(">>> UUID[{}]: {}", i, uuidList.get(i));
            }
        } catch (IllegalArgumentException e) {
            log.error(">>> Invalid UUID format in ids parameter: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }

        if (uuidList.isEmpty()) {
            log.warn(">>> No valid UUIDs found in ids parameter");
            return ResponseEntity.badRequest().build();
        }

        if (uuidList.size() > 100) {
            log.warn(">>> Too many IDs requested: {}. Maximum is 100", uuidList.size());
            return ResponseEntity.badRequest().build();
        }

        log.info(">>> Calling profileService.getMany with {} UUIDs", uuidList.size());
        List<SharedProfileDto> results = profileService.getMany(uuidList);
        log.info(">>> Returning {} profiles out of {} requested", results.size(), uuidList.size());

        if (results.isEmpty()) {
            log.error(">>> WARNING: Got 0 results from database!");
        }

        return ResponseEntity.ok(results);
    }


    @GetMapping("/deck")
    public ResponseEntity<List<SharedProfileDto>> getDeck(
            @RequestParam UUID viewerId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        // Validate parameters
        if (offset < 0 || limit < 1 || limit > 100) {
            log.warn("Invalid deck parameters: offset={}, limit={}. Limit must be 1-100", offset, limit);
            return ResponseEntity.badRequest().build();
        }

        List<SharedProfileDto> deck = deckService.listWithProfiles(viewerId, offset, limit);
        return ResponseEntity.ok(deck);
    }

    @GetMapping("/active")
    public ResponseEntity<List<SharedProfileDto>> getActiveUsers() {
        List<SharedProfileDto> activeUsers = profileService.getActiveUsers();
        log.debug("Fetched {} active users", activeUsers.size());
        return ResponseEntity.ok(activeUsers);
    }

}
