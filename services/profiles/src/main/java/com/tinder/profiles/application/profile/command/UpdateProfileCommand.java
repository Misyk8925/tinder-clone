package com.tinder.profiles.application.profile.command;

import com.tinder.contracts.dto.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;

import java.util.List;

/** Application-layer intent to fully update a profile (PUT semantics). */
public record UpdateProfileCommand(
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
}
