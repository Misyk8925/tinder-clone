package com.tinder.profiles.profile;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.exception.ProfileValidationException;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileDomainService {

    private final InputSanitizationService sanitizationService;

    public void validatePreferencesBusinessRules(@NonNull PreferencesDto preferences) {
        // Cross-field validation: minAge must not be greater than maxAge
        if (preferences.getMinAge() != null && preferences.getMaxAge() != null) {
            if (preferences.getMinAge() > preferences.getMaxAge()) {
                throw new ProfileValidationException("Minimum age cannot be greater than maximum age");
            }
        }
    }

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


    public void updateProfileFromDto(@NonNull Profile profile, @NonNull CreateProfileDtoV1 dto) {
        profile.updateBasicInfo(
                dto.name(),
                dto.age(),
                dto.gender(),
                dto.bio(),
                dto.city()
        );
    }




    public boolean canDeleteProfile(@NonNull Profile profile) {
        // Add business rules here, e.g., check if profile has pending matches
        return !profile.isDeleted();
    }


    public void markAsDeleted(@NonNull Profile profile) {
        profile.markAsDeleted();
    }

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

