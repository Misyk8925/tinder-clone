package com.tinder.profiles.profile.dto.profileData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import jakarta.validation.constraints.*;
import lombok.Value;

/**
 * DTO for {@link Profile}
 */
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateProfileDtoV1 {

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

    @JsonCreator
    public CreateProfileDtoV1(
        @JsonProperty("name") String name,
        @JsonProperty("age") Integer age,
        @JsonProperty("bio") String bio,
        @JsonProperty("city") String city,
        @JsonProperty("preferences") PreferencesDto preferences
    ) {
        this.name = name;
        this.age = age;
        this.bio = bio;
        this.city = city;
        this.preferences = preferences;
    }
}