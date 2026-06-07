package com.tinder.profiles.application.profile.port.out;

/**
 * Outbound port for resolving a location to coordinates + canonical city. The
 * implementing adapter delegates to the standalone location service (with local
 * geocoding fallback) and returns domain-friendly values, so the application
 * layer never sees the persistence {@code Location} entity or the remote
 * transport.
 */
public interface LocationPort {

    /** Resolves a city name to coordinates + the canonical stored city. */
    ResolvedLocation resolve(String city);

    /**
     * Resolves explicit GPS coordinates (associating them with {@code city} for
     * reverse-geocoding / storage), returning the canonical resolved location.
     */
    ResolvedLocation resolveFromCoordinates(double latitude, double longitude, String city);
}
