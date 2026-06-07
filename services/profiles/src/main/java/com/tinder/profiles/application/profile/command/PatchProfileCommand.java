package com.tinder.profiles.application.profile.command;

import com.tinder.contracts.dto.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;

import java.util.List;

/**
 * Application-layer intent to partially update a profile (PATCH semantics). A
 * {@code null} field means "not provided".
 */
public record PatchProfileCommand(
        String userId,
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        MatchingPreferences preferences,
        List<Hobby> hobbies,
        Double latitude,
        Double longitude
) {
    public boolean hasAnyField() {
        return name != null || age != null || gender != null || bio != null
                || city != null || preferences != null || hobbies != null
                || latitude != null || longitude != null;
    }
}
