package com.tinder.profiles.preferences;

import lombok.Value;

/**
 * DTO for {@link Preferences}
 */
@Value
public class PreferencesDto {
    Integer minAge;
    Integer maxAge;
    String gender;
    Integer maxRange;
}