package com.tinder.profiles.profile.internal;


import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final InternalProfileService profileService;
    private final DeckService deckService;


    @GetMapping("/search")
    public List<GetProfileDto> search(@RequestParam UUID viewerId,
                                      @RequestParam(required = false) String gender,
                                      @RequestParam(required = false) Integer minAge,
                                      @RequestParam(required = false) Integer maxAge,
                                      @RequestParam(required = false) Integer maxRange,
                                      @RequestParam(defaultValue = "2000") Integer limit) {
        PreferencesDto prefs = new PreferencesDto(viewerId, minAge, maxAge, gender, maxRange);
        return profileService.searchByViewerPrefs(viewerId, prefs, limit);
    }

    @GetMapping("/page")
    public List<GetProfileDto> page(@RequestParam int page,
                                    @RequestParam int size) {
        return profileService.fetchPage(page, size);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<GetProfileDto>> getMany(@RequestParam List<UUID> ids) {
        if ( ids.isEmpty())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(profileService.getMany(ids));
    }

    /**
     * Get deck for a user
     * Reads from Redis cache (populated by Deck Service)
     * Falls back to on-the-fly building for new users
     */
    @GetMapping("/deck")
    public ResponseEntity<List<GetProfileDto>> getDeck(
            @RequestParam UUID viewerId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        List<GetProfileDto> deck = deckService.listWithProfiles(viewerId, offset, limit);
        return ResponseEntity.ok(deck);
    }

    @GetMapping("/active")
    public ResponseEntity<List<GetProfileDto>> getActiveUsers() {
        return ResponseEntity.ok(profileService.getActiveUsers());
    }


}
