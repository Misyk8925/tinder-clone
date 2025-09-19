package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.errors.CustomErrorResponse;
import com.tinder.profiles.profile.dto.errors.ErrorDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor

public class ProfileController {

    private final ProfileServiceImpl service;

    @GetMapping
    public PagedModel<Profile> getAll(Pageable pageable) {
        Page<Profile> profiles = service.getAll(pageable);
        return new PagedModel<>(profiles);
    }

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
            ErrorDetails errorDetails = ErrorDetails.builder()
                    .code("PROFILE_EXISTS")
                    .message("User already has a profile")
                    .build();
            CustomErrorResponse errorResponse = new CustomErrorResponse(errorDetails);

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorResponse);
        }
        Profile result = service.create(profile);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", result.getProfileId()));
    }

    @PatchMapping("/{id}")
    public Profile patch(@PathVariable UUID id, @RequestBody JsonNode patchNode) throws IOException {
        return service.patch(id, patchNode);
    }

    @PatchMapping
    public List<UUID> patchMany(@RequestParam List<UUID> ids, @RequestBody JsonNode patchNode) throws IOException {
        return service.patchMany(ids, patchNode);
    }

    @DeleteMapping("/{id}")
    public Profile delete(@PathVariable UUID id) {
        return service.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<UUID> ids) {
        service.deleteMany(ids);
    }
}
