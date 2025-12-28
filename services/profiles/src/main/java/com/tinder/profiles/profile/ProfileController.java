package com.tinder.profiles.profile;

import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;
    private final ProfileApplicationService applicationService;
    private final DeckService deckService;

    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getOne(id));
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


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-many")
    public void deleteMany(@RequestParam List<UUID> ids) {
        applicationService.deleteMany(ids);
    }

}
