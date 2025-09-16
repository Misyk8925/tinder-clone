package com.tinder.profiles.profile;

import jakarta.validation.constraints.*;
import lombok.Value;

/**
 * DTO for {@link Profile}
 */
@Value
public class ProfileDto {

    @NotNull(message = "name is required")
    @NotEmpty(message = "name is required")
    @NotBlank(message = "name is required")
    String firstName;

    @NotNull(message = "age is required")
    @Min(message = "you must be older than 18 y.o,", value = 18)
    @Max(message = "you must be younger than 130 y.o,", value = 130)
    Integer age;

    String bio;

    @NotNull(message = "city is required")
    @NotEmpty(message = "city is required")
    @NotBlank(message = "city is required")
    String city;
}