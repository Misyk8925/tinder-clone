package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Fixture builders shared by the scoring strategy tests. */
class ScoringTestProfiles {

    private ScoringTestProfiles() {
    }

    static SharedProfileDto profile(Integer age, SharedPreferencesDto preferences,
                                    Double latitude, Double longitude) {
        SharedLocationDto location = latitude == null || longitude == null
                ? null
                : new SharedLocationDto(UUID.randomUUID(), latitude, longitude, "Test City",
                        LocalDateTime.now(), LocalDateTime.now());
        return new SharedProfileDto(UUID.randomUUID(), "Test", age, "Bio", "Test City", true,
                location, preferences, false, List.of(), List.of());
    }

    static SharedProfileDto profile(Integer age, SharedPreferencesDto preferences) {
        return profile(age, preferences, 0.0, 0.0);
    }

    static SharedPreferencesDto preferences(int minAge, int maxAge, int maxRange) {
        return new SharedPreferencesDto(minAge, maxAge, "ANY", maxRange);
    }
}
