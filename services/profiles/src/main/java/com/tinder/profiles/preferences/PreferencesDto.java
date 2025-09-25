package com.tinder.profiles.preferences;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.UUID;

/**
 * DTO for {@link Preferences}
 */
@Value
public class PreferencesDto {
    Integer minAge;
    Integer maxAge;
    String gender;
    Integer maxRange;

    @JsonCreator
    public PreferencesDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("minAge") Integer minAge,
        @JsonProperty("maxAge") Integer maxAge,
        @JsonProperty("gender") String gender,
        @JsonProperty("maxRange") Integer maxRange
    ) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.gender = gender;
        this.maxRange = maxRange;
    }
}