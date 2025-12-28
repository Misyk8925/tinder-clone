package com.tinder.profiles.profile;

import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Facade service that delegates to ProfileApplicationService.
 * Kept for backward compatibility with existing controllers.
 * @deprecated Use ProfileApplicationService directly instead
 */
@Deprecated
@RequiredArgsConstructor
@Service
@Slf4j
public class ProfileService {

    private final ProfileApplicationService applicationService;

    public Page<Profile> getAll(Pageable pageable) {
        return applicationService.getAll(pageable);
    }

    public GetProfileDto getOne(UUID id) {
        return applicationService.getOne(id);
    }

    public Profile getByUsername(String username) {
        return applicationService.getByUsername(username);
    }

    public Profile getByUserId(String userId) {
        return applicationService.getByUserId(userId);
    }

    public Profile create(CreateProfileDtoV1 profile, String userId) {
        return applicationService.create(profile, userId);
    }

    public Profile update(CreateProfileDtoV1 profile, String userId) {
        return applicationService.update(profile, userId);
    }

    public Profile patch(String id, PatchProfileDto patchDto) {
        return applicationService.patch(id, patchDto);
    }

    public Profile delete(UUID id) {
        return applicationService.delete(id);
    }

    public void deleteMany(List<UUID> ids) {
        applicationService.deleteMany(ids);
    }
}
