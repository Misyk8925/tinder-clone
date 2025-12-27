package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.ProfileDto;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.errors.CustomErrorResponse;
import com.tinder.profiles.profile.dto.errors.ErrorSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;
    private final DeckService deckService;

    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getOne(id));
    }

    @PostMapping("/")
    public ResponseEntity<Object> create(@RequestBody @Valid CreateProfileDtoV1 profile, @AuthenticationPrincipal Jwt jwt) {

        String sub = jwt.getSubject();
        boolean profileExists = service.getByUserId(sub) != null;
        if (profileExists) {
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("PROFILE_EXISTS")
                    .message("User already has a profile")
                    .build();
            CustomErrorResponse errorResponse = new CustomErrorResponse(errorSummary, null);

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorResponse);
        }
        Profile result = service.create(profile, sub);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", result.getProfileId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @RequestBody @Valid CreateProfileDtoV1 profile, @AuthenticationPrincipal Jwt jwt)  {

        String sub = jwt.getSubject();
        Profile existingProfile = service.getByUserId(sub);
        if (existingProfile == null || !existingProfile.getProfileId().equals(id)) {
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("UNAUTHORIZED_UPDATE")
                    .message("You are not authorized to update this profile")
                    .build();
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new CustomErrorResponse(errorSummary, null));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.update(id, profile));
    }

    @PatchMapping("/{id}")
    public Profile patch(@PathVariable UUID id, @RequestBody JsonNode patchNode) throws IOException {
        return service.patch(id, patchNode);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-many")
    public void deleteMany(@RequestParam List<UUID> ids) {
        service.deleteMany(ids);
    }

    @GetMapping("/search")
    public List<GetProfileDto> search(@RequestParam UUID viewerId,
                                   @RequestParam(required = false) String gender,
                                   @RequestParam(required = false) Integer minAge,
                                   @RequestParam(required = false) Integer maxAge,
                                   @RequestParam(required = false) Integer maxRange,
                                   @RequestParam(defaultValue = "2000") Integer limit) {
        PreferencesDto prefs = new PreferencesDto(viewerId, minAge, maxAge, gender, maxRange);
        return service.searchByViewerPrefs(viewerId, prefs, limit);
    }

    @GetMapping("/page")
    public List<ProfileDto> page(@RequestParam int page,
                                 @RequestParam int size) {
        return service.fetchPage(page, size);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<GetProfileDto>> getMany(@RequestParam List<UUID> ids) {
        if ( ids.isEmpty())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(service.getMany(ids));
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
        return ResponseEntity.ok(service.getActiveUsers());
    }
}
