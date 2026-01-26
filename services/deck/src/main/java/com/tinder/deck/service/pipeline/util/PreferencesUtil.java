package com.tinder.deck.service.pipeline.util;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for preferences validation and defaults
 */
@Slf4j
@UtilityClass
public class PreferencesUtil {

    private static final int DEFAULT_MIN_AGE = 18;
    private static final int DEFAULT_MAX_AGE = 50;
    private static final String DEFAULT_GENDER = "ANY";
    private static final int DEFAULT_MAX_RANGE = 100;

    /**
     * Get preferences or return defaults if null
     *
     * @param viewer The viewer profile
     * @return Preferences DTO (never null)
     */
    public static SharedPreferencesDto getPreferencesOrDefault(SharedProfileDto viewer) {
        if (viewer.preferences() == null) {
            log.warn("Viewer {} has null preferences, using defaults", viewer.id());
            return new SharedPreferencesDto(
                    DEFAULT_MIN_AGE,
                    DEFAULT_MAX_AGE,
                    DEFAULT_GENDER,
                    DEFAULT_MAX_RANGE
            );
        }
        return viewer.preferences();
    }
}
