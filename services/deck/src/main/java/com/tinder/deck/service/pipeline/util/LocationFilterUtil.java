package com.tinder.deck.service.pipeline.util;

import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.scoring.LocationProximityStrategy;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for location-based filtering of candidates
 */
@Slf4j
@UtilityClass
public class LocationFilterUtil {

    /**
     * Check if candidate is within viewer's max range
     *
     * @param viewer    The viewer profile
     * @param candidate The candidate profile
     * @param maxRange  Maximum distance in kilometers
     * @return true if within range or location data is missing
     */
    public static boolean isWithinRange(SharedProfileDto viewer, SharedProfileDto candidate, int maxRange) {
        if (viewer.location() == null || candidate.location() == null) {
            log.debug("Missing location data for viewer {} or candidate {}, skipping location filter",
                    viewer.id(), candidate.id());
            return true; // No location filtering if data missing
        }

        double distance = LocationProximityStrategy.calculateDistance(
                viewer.location().geo(),
                candidate.location().geo()
        );

        boolean withinRange = distance <= maxRange;

        if (!withinRange) {
            log.debug("Candidate {} filtered out: distance {}km > maxRange {}km",
                    candidate.id(), Math.round(distance), maxRange);
        }

        return withinRange;
    }
}
