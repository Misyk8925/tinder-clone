package com.tinder.profiles.profile.internal;


import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.profile.IdsQueryParamParser;
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
@RequestMapping("/api/v1/profiles/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final InternalProfileService profileService;
    private final DeckService deckService;
    private final IdsQueryParamParser idsQueryParamParser;

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
        try {
            List<UUID> uuidList = idsQueryParamParser.parse(ids);
            List<SharedProfileDto> results = profileService.getMany(uuidList);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ids parameter for /internal/by-ids: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
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
