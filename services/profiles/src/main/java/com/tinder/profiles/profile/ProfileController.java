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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    @GetMapping("/test-pa")
    @PreAuthorize("hasAnyRole('AMI')")
    private ResponseEntity<String> testPa() {
        return ResponseEntity.ok("test pa");
    }

    private final ProfileServiceImpl service;
    private final DeckService deckService;



    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getOne(id));
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody @Valid CreateProfileDtoV1 profile) {

        boolean profileExists = service.getByUsername(profile.getName()) != null;
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
        Profile result = service.create(profile);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", result.getProfileId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable UUID id, @RequestBody @Valid CreateProfileDtoV1 profile) {

        if (service.getByUsername(profile.getName()) == null) {
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("NO_SUCH_PROFILE")
                    .message("There is no such profile")
                    .build();
            CustomErrorResponse errorResponse = new CustomErrorResponse(errorSummary, null);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorResponse);
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

    @DeleteMapping
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

    // Соответствует: GET /page?page=&size=
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
}
