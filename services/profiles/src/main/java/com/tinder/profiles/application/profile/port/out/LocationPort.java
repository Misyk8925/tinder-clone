package com.tinder.profiles.application.profile.port.out;

/**
 * Outbound port for resolving a location to coordinates + canonical city. The
 * implementing adapter delegates to the standalone location service (with local
 * geocoding fallback) and returns domain-friendly values, so the application
 * layer never sees the persistence {@code Location} entity or the remote
 * transport.
 */
public interface LocationPort {

    /**
     * Resolves a location: by GPS coordinates when {@code latitude}/{@code longitude}
     * are present (associating them with {@code city} for storage), otherwise by
     * city name. Returns the canonical coordinates + stored city.
     */
    ResolvedLocation resolve(Double latitude, Double longitude, String city);
}
