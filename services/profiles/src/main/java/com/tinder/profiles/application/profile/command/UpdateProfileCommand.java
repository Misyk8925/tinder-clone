package com.tinder.profiles.application.profile.command;

import com.tinder.profiles.application.profile.model.PreferencesData;

import java.util.List;

/** Application-layer intent to fully update a profile (PUT semantics). */
public record UpdateProfileCommand(
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
}
