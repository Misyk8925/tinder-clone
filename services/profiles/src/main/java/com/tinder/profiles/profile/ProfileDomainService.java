package com.tinder.profiles.profile;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service containing core business logic for Profile entity.
 * Focuses on domain rules NOT covered by Bean Validation annotations.
 *
 * Note: Basic field validations (length, format, nullability) are handled
 * by @Valid annotations in CreateProfileDtoV1 and PreferencesDto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileDomainService {

    private final InputSanitizationService sanitizationService;

    /**
     * Validates cross-field business rules for preferences.
     * Bean Validation handles individual field constraints.
     */
    public void validatePreferencesBusinessRules(@NonNull PreferencesDto preferences) {
        // Cross-field validation: minAge must not be greater than maxAge
        if (preferences.getMinAge() != null && preferences.getMaxAge() != null) {
            if (preferences.getMinAge() > preferences.getMaxAge()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum age cannot be greater than maximum age");
            }
        }
    }

    /**
     * Sanitizes profile data to prevent XSS and injection attacks.
     * This is infrastructure concern, but kept here for consistency.
     */
    public @NonNull CreateProfileDtoV1 sanitizeProfileData(@NonNull CreateProfileDtoV1 profile) {
        return new CreateProfileDtoV1(
                sanitizationService.sanitizePlainText(profile.name()),
                profile.age(),
                sanitizationService.sanitizePlainText(profile.gender()),
                profile.bio() != null ? sanitizationService.sanitizePlainText(profile.bio()) : null,
                sanitizationService.sanitizePlainText(profile.city()),
                profile.preferences()
        );
    }

    /**
     * Updates profile fields from DTO
     */
    public void updateProfileFromDto(@NonNull Profile profile, @NonNull CreateProfileDtoV1 dto) {
        profile.updateBasicInfo(
                dto.name(),
                dto.age(),
                dto.gender(),
                dto.bio(),
                dto.city()
        );
    }

    /**
     * Updates or creates preferences for a profile
     */
    public Preferences updateOrCreatePreferences(Profile profile, PreferencesDto preferencesDto) {
        // Always create a new Preferences instance
        // Do NOT mutate existing preferences as they might be shared
        return Preferences.builder()
                .minAge(preferencesDto.getMinAge())
                .maxAge(preferencesDto.getMaxAge())
                .gender(preferencesDto.getGender())
                .maxRange(preferencesDto.getMaxRange() != null ? preferencesDto.getMaxRange() : 50)
                .build();
    }

    /**
     * Checks if profile can be deleted (business rule)
     */
    public boolean canDeleteProfile(@NonNull Profile profile) {
        // Add business rules here, e.g., check if profile has pending matches
        return !profile.isDeleted();
    }

    /**
     * Marks profile as deleted (soft delete)
     */
    public void markAsDeleted(@NonNull Profile profile) {
        profile.markAsDeleted();
    }

    /**
     * Activates a profile
     */
    public void activateProfile(@NonNull Profile profile) {
        profile.activate();
    }

    /**
     * Deactivates a profile
     */
    public void deactivateProfile(@NonNull Profile profile) {
        profile.deactivate();
    }
}

