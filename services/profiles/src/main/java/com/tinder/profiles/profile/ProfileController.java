package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
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
import org.springframework.web.server.ResponseStatusException;

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

        try {
            Profile newProfile = service.create(profile, jwt.getSubject());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.created("Profile created successfully", newProfile.getProfileId()));
        } catch (ResponseStatusException e) {
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("PROFILE_EXISTS")
                    .message("User already has a profile")
                    .build();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorSummary);
        } catch (Exception e){
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("INTERNAL_ERROR")
                    .message("An unexpected error occurred")
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorSummary);
        }
    }

    @PutMapping("/")
    public ResponseEntity<Object> update(@RequestBody @Valid CreateProfileDtoV1 profile, @AuthenticationPrincipal Jwt jwt)  {

        try {
            Profile updatedProfile = service.update(profile, jwt.getSubject());
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success("Profile updated successfully", updatedProfile.getProfileId()));
        } catch (ResponseStatusException e) {
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("PROFILE_NOT_FOUND")
                    .message("Profile not found")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorSummary);
        } catch (Exception e){
            ErrorSummary errorSummary = ErrorSummary.builder()
                    .code("INTERNAL_ERROR")
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorSummary);
        }
    }

    @PatchMapping("/")
    public Profile patch(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode patchNode) throws IOException {
        try {
            String userId = jwt.getSubject();
            return service.patch(userId, patchNode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

}
