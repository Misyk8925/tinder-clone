package com.tinder.profiles.application.profile.command;

import com.tinder.contracts.dto.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;

import java.util.List;

/**
 * Application-layer intent to create a profile. Built by the API mapper from the
 * inbound DTO after sanitization; carries domain value objects, not transport types.
 */
public record CreateProfileCommand(
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
