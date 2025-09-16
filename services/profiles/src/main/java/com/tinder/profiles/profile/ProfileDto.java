package com.tinder.profiles.profile;

import com.tinder.profiles.preferences.PreferencesDto;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.container.PreMatching;
import lombok.Value;

/**
 * DTO for {@link Profile}
 */
@Value
public class ProfileDto {

    @NotBlank(message = "name is required")
    String name;

    @NotNull(message = "age is required")
    @Min(message = "you must be older than 18 y.o,", value = 18)
    @Max(message = "you must be younger than 130 y.o,", value = 130)
    Integer age;

    @Size(max = 1023, message = "bio must be less than 1000 characters")
    String bio;

    @NotBlank(message = "city is required")
    String city;

    @NotNull(message = "preferences are required")
    PreferencesDto preferences;
}