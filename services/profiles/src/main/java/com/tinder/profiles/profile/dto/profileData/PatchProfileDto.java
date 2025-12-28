package com.tinder.profiles.profile.dto.profileData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tinder.profiles.profile.Profile;
import jakarta.validation.constraints.*;

/**
 * DTO for partial updates (PATCH) of {@link Profile}
 * All fields are optional, but if provided, they must be valid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchProfileDto(
        @Size(min = 2, max = 50, message = "name must be between 2-50 characters")
        String name,

        @Min(message = "you must be older than 18 y.o.", value = 18)
        @Max(message = "you must be younger than 130 y.o.", value = 130)
        Integer age,

        @Pattern(
                regexp = "^(male|female|other)$",
                message = "gender must be male, female, or other",
                flags = Pattern.Flag.CASE_INSENSITIVE
        )
        String gender,

        @Size(max = 1023, message = "bio must be less than 1000 characters")
        String bio,

        @Size(max = 100, message = "city name too long")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ\\s-]+$",
                message = "city can only contain letters, spaces, and hyphens"
        )
        String city
) {
    /**
     * Checks if any field is provided
     */
    public boolean hasAnyField() {
        return name != null || age != null || gender != null || bio != null || city != null;
    }
}

