package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.tinder.profiles.preferences.PreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileServiceImpl profileServiceImpl;

    @GetMapping
    public PagedModel<Profile> getAll(Pageable pageable) {
        Page<Profile> profiles = profileServiceImpl.getAll(pageable);
        return new PagedModel<>(profiles);
    }

    @GetMapping("/{id}")
    public Profile getOne(@PathVariable UUID id) {
        return profileServiceImpl.getOne(id);
    }

    @GetMapping("/by-ids")
    public List<Profile> getMany(@RequestParam List<UUID> ids) {
        return profileServiceImpl.getMany(ids);
    }

    @PostMapping
    public Profile create(@RequestBody CreateProfileDtoV1 profile) {
        return profileServiceImpl.create(profile);
    }

    @PatchMapping("/{id}")
    public Profile patch(@PathVariable UUID id, @RequestBody JsonNode patchNode) throws IOException {
        return profileServiceImpl.patch(id, patchNode);
    }

    @PatchMapping
    public List<UUID> patchMany(@RequestParam List<UUID> ids, @RequestBody JsonNode patchNode) throws IOException {
        return profileServiceImpl.patchMany(ids, patchNode);
    }

    @DeleteMapping("/{id}")
    public Profile delete(@PathVariable UUID id) {
        return profileServiceImpl.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<UUID> ids) {
        profileServiceImpl.deleteMany(ids);
    }
}
