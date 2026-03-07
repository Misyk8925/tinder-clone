package com.tinder.profiles.profile;

import com.tinder.profiles.kafka.dto.ChangeType;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.exception.ProfileValidationException;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileDomainService {

    private final InputSanitizationService sanitizationService;

    public void validatePreferencesBusinessRules(@NonNull PreferencesDto preferences) {
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

    /**
     * Returns a sanitized copy of the patch DTO, leaving null fields untouched.
     */
    public @NonNull PatchProfileDto sanitizePatchData(@NonNull PatchProfileDto patch) {
        return new PatchProfileDto(
                patch.name() != null ? sanitizationService.sanitizePlainText(patch.name()) : null,
                patch.age(),
                patch.gender() != null ? sanitizationService.sanitizePlainText(patch.gender()) : null,
                patch.bio() != null ? sanitizationService.sanitizePlainText(patch.bio()) : null,
                patch.city() != null ? sanitizationService.sanitizePlainText(patch.city()) : null,
                patch.preferences()
        );
    }

    public boolean canDeleteProfile(@NonNull Profile profile) {
        return !profile.isDeleted();
    }

    /**
     * Determines the highest-priority change type from a set of changed field names.
     *
     * Priority order:
     * 1. LOCATION_CHANGE — city changed; affects both owner's deck and viewers' decks
     * 2. PREFERENCES     — matching preferences changed; affects owner's deck
     * 3. CRITICAL_FIELDS — age or gender changed; affects visibility in other decks
     * 4. NON_CRITICAL    — bio, name, etc.; lowest priority
     */
    public ChangeType determineChangeType(@NonNull Set<String> changedFields, boolean preferencesChanged) {
        if (changedFields.contains("city")) {
            return ChangeType.LOCATION_CHANGE;
        }
        if (preferencesChanged) {
            return ChangeType.PREFERENCES;
        }
        if (changedFields.contains("age") || changedFields.contains("gender")) {
            return ChangeType.CRITICAL_FIELDS;
        }
        return ChangeType.NON_CRITICAL;
    }
}
