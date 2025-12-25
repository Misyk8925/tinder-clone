package com.tinder.profiles.profile.dto.profileData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Value;

/**
 * DTO for {@link Profile}
 */
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateProfileDtoV1 {

    @NotBlank(message = "name is required")
    @Size(min = 2, max = 50, message = "name must be between 2-50 characters")
    String name;

    @NotNull(message = "age is required")
    @Min(message = "you must be older than 18 y.o,", value = 18)
    @Max(message = "you must be younger than 130 y.o,", value = 130)
    Integer age;

    @NotBlank(message = "gender is required")
    @Pattern(regexp = "^(male|female|other)$",
            message = "gender must be male, female, or other",
            flags = Pattern.Flag.CASE_INSENSITIVE)
    String gender;

    @Size(max = 1023, message = "bio must be less than 1000 characters")
    String bio;

    @NotBlank(message = "city is required")
    @Size(max = 100, message = "city name too long")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s-]+$",
            message = "city can only contain letters, spaces, and hyphens")
    String city;

    @NotNull(message = "preferences are required")
    @Valid
    PreferencesDto preferences;

    @JsonCreator
    public CreateProfileDtoV1(
            @JsonProperty("name") String name,
            @JsonProperty("age") Integer age,
            @JsonProperty("gender") String gender,
            @JsonProperty("bio") String bio,
            @JsonProperty("city") String city,
            @JsonProperty("preferences") PreferencesDto preferences
    ) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.bio = bio;
        this.city = city;
        this.preferences = preferences;
    }
}