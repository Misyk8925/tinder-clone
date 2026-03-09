package com.tinder.profiles.profile;

import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileApplicationService applicationService;
    private final DeckService deckService;

    @GetMapping("/deck")
    public ResponseEntity<List<SharedProfileDto>> getDeck(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        // Resolve profileId from the authenticated user's JWT subject (Keycloak userId)
        Profile viewer = applicationService.getByUserId(jwt.getSubject());
        if (viewer == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("GET /deck called for userId={}, profileId={}, offset={}, limit={}",
                jwt.getSubject(), viewer.getProfileId(), offset, limit);

        List<SharedProfileDto> deck = deckService.listWithProfiles(viewer.getProfileId(), offset, limit);
        return ResponseEntity.ok(deck);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        log.info("getOne called with id: {}", id);
        return ResponseEntity.ok(applicationService.getOne(id));
    }

    @PostMapping("/")
    public ResponseEntity<Object> create(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        Profile newProfile = applicationService.create(profile, jwt.getSubject());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", newProfile.getProfileId()));
    }

    @PutMapping("/")
    public ResponseEntity<Object> update(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        Profile updatedProfile = applicationService.update(profile, jwt.getSubject());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Profile updated successfully", updatedProfile.getProfileId()));
    }

    @PatchMapping("/")
    public Profile patch(@AuthenticationPrincipal Jwt jwt,
                        @RequestBody @Valid PatchProfileDto patchDto) {
        return applicationService.patch(jwt.getSubject(), patchDto);
    }


    @DeleteMapping("/")
    public ResponseEntity<Void> delete(
                                       @AuthenticationPrincipal Jwt jwt) {
        applicationService.delete(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-many")
    public void deleteMany(@RequestParam List<UUID> ids) {
        applicationService.deleteMany(ids);
    }

}
