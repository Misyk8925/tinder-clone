package com.tinder.profiles.api.profile.dto.profileData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** API-owned matching-preferences payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferencesDto(
        @Min(18) @Max(130) Integer minAge,
        @Min(18) @Max(130) Integer maxAge,
        @Pattern(regexp = "^(male|female|other|all)$", flags = Pattern.Flag.CASE_INSENSITIVE)
        String gender,
        @Min(1) @Max(500) Integer maxRange
) {
}
