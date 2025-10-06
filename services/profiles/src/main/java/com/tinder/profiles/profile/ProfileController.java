package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
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
}
