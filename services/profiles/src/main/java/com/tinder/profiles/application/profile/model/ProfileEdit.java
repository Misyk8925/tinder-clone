package com.tinder.profiles.application.profile.model;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;

import java.util.List;

/** Application-layer representation of a full or partial profile edit. */
public record ProfileEdit(
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        GeoPoint position,
        MatchingPreferences preferences,
        List<Hobby> hobbies
) {
    public boolean hasCity() {
        return city != null && !city.isBlank();
    }

    public boolean hasPosition() {
        return position != null;
    }
}
