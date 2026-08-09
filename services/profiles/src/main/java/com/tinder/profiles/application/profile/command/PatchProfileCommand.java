package com.tinder.profiles.application.profile.command;

import com.tinder.profiles.application.profile.model.PreferencesData;

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
        PreferencesData preferences,
        List<String> hobbies,
        Double latitude,
        Double longitude
) implements ProfileEditCommand {

    public boolean hasAnyField() {
        return name != null || age != null || gender != null || bio != null
                || city != null || preferences != null || hobbies != null
                || latitude != null || longitude != null;
    }
}
