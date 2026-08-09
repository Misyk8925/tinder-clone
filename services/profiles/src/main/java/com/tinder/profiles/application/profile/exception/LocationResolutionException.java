package com.tinder.profiles.application.profile.exception;

/**
 * Thrown when a profile's location cannot be resolved: the location service is
 * unavailable and the city has no previously-resolved local fallback. Maps to
 * 503 so clients retry instead of treating it as a permanent failure.
 */
public class LocationResolutionException extends ProfileException {

    public LocationResolutionException(String city) {
        super(
            "Location could not be resolved for city '%s': location service unavailable and no local fallback".formatted(city),
            "LOCATION_RESOLUTION_ERROR"
        );
    }
}
