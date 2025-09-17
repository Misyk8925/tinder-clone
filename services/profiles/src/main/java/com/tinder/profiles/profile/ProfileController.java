package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
    public Profile getOne(@PathVariable UUID id) {
        return service.getOne(id);
    }

    @GetMapping("/by-ids")
    public List<Profile> getMany(@RequestParam List<UUID> ids) {
        return service.getMany(ids);
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody @Valid CreateProfileDtoV1 profile) {

        Profile saved = service.create(profile);


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully"));
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
