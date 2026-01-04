package com.tinder.profiles.profile.internal;


import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
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
    public ResponseEntity<List<GetProfileDto>> search(
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
        List<GetProfileDto> results = profileService.searchByViewerPrefs(viewerId, prefs, limit);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/page")
    public ResponseEntity<List<GetProfileDto>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Validate pagination parameters
        if (page < 0 || size < 1 || size > 100) {
            log.warn("Invalid pagination parameters: page={}, size={}. Size must be 1-100", page, size);
            return ResponseEntity.badRequest().build();
        }

        List<GetProfileDto> results = profileService.fetchPage(page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<GetProfileDto>> getMany(@RequestParam List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("Empty or null ids list provided to /by-ids");
            return ResponseEntity.badRequest().build();
        }

        if (ids.size() > 100) {
            log.warn("Too many IDs requested: {}. Maximum is 100", ids.size());
            return ResponseEntity.badRequest().build();
        }

        List<GetProfileDto> results = profileService.getMany(ids);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/deck")
    public ResponseEntity<List<GetProfileDto>> getDeck(
            @RequestParam UUID viewerId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        // Validate parameters
        if (offset < 0 || limit < 1 || limit > 100) {
            log.warn("Invalid deck parameters: offset={}, limit={}. Limit must be 1-100", offset, limit);
            return ResponseEntity.badRequest().build();
        }

        List<GetProfileDto> deck = deckService.listWithProfiles(viewerId, offset, limit);
        return ResponseEntity.ok(deck);
    }

    @GetMapping("/active")
    public ResponseEntity<List<GetProfileDto>> getActiveUsers() {
        List<GetProfileDto> activeUsers = profileService.getActiveUsers();
        log.debug("Fetched {} active users", activeUsers.size());
        return ResponseEntity.ok(activeUsers);
    }

}
