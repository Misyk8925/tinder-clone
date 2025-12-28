package com.tinder.profiles.preferences;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Value;

import java.util.UUID;

/**
 * DTO for {@link Preferences}
 */
@Value
public class PreferencesDto {
    @Min(value = 18, message = "Minimum age preference must be at least 18")
    @Max(value = 130, message = "Maximum age preference cannot exceed 130")
    Integer minAge;

    @Min(value = 18, message = "Maximum age preference must be at least 18")
    @Max(value = 130, message = "Maximum age preference cannot exceed 130")
    Integer maxAge;

    @Pattern(regexp = "^(male|female|other|all)$",
             message = "Gender preference must be male, female, other, or all",
             flags = Pattern.Flag.CASE_INSENSITIVE)
    String gender;

    @Min(value = 1, message = "Maximum range must be at least 1 km")
    @Max(value = 500, message = "Maximum range cannot exceed 500 km")
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

