package com.tinder.profiles.profile.internal;

import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalProfileService {

    private final ProfileRepository repo;
    private final GetProfileMapper getMapper;
    private final InputSanitizationService sanitizationService;


    public List<GetProfileDto> fetchPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return repo.findAll(pageable).stream()
                .map(getMapper::toGetProfileDto)
                .toList();
    }

    public List<GetProfileDto> searchByViewerPrefs(UUID viewerId, PreferencesDto p, int limit) {

        Profile viewer = repo.findById(viewerId)
                .orElseThrow(() -> new NoSuchElementException("viewer not found"));

        List<Profile> allProfiles = repo.findAll(PageRequest.of(0, Math.max(limit, 1))).stream()
                .filter(profile -> !profile.isDeleted())
                .toList();

        log.debug("searchByViewerPrefs: viewer {} fetched {} profiles from repo", viewerId, allProfiles.size());

        PreferencesDto prefs = p;

        List<Profile> base = allProfiles.stream()

                .filter(profile -> {
                    Integer age = profile.getAge();
                    return age != null && age >= prefs.getMinAge() && age <= prefs.getMaxAge();
                })
                .filter(profile -> {
                    String prefGender = prefs.getGender();
                    if (prefGender == null || prefGender.isEmpty() || prefGender.equalsIgnoreCase("any")) {
                        return true;
                    }
                    return profile.getGender() != null && profile.getGender().equalsIgnoreCase(prefGender);
                })
                .limit(limit)
                .toList();

        log.debug("searchByViewerPrefs: viewer {} filtered to {} candidates by prefs", viewerId, base.size());

        return base.stream().map(getMapper::toGetProfileDto).toList();
    }


    public List<GetProfileDto> getMany(List<UUID> ids) {
        return repo.findAllById(ids).stream()
                .map(getMapper::toGetProfileDto)
                .toList();
    }

    public List<GetProfileDto> getActiveUsers() {
        return repo.findAllByIsDeletedFalse().stream()
                .map(getMapper::toGetProfileDto)
                .toList();
    }


    private @NonNull CreateProfileDtoV1 getSanitizedProfile(CreateProfileDtoV1 profile) {
        return new CreateProfileDtoV1(
                sanitizationService.sanitizePlainText(profile.name()),
                profile.age(),
                sanitizationService.sanitizePlainText(profile.gender()),
                sanitizationService.sanitizePlainText(profile.bio()),
                sanitizationService.sanitizePlainText(profile.city()),
                profile.preferences()
        );
    }
}
