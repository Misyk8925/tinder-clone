package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.GeoPoint;

/**
 * Outbound port for resolving a location to coordinates. The implementing
 * adapter ({@code infrastructure.profile.location.LocationResolverAdapter})
 * delegates to the standalone location service (with local geocoding fallback)
 * and returns a domain {@link GeoPoint}, so the application layer never sees the
 * persistence {@code Location} entity or the remote transport.
 */
public interface LocationPort {

    /** Resolves a city name to coordinates. */
    GeoPoint resolve(String city);

    /**
     * Resolves explicit GPS coordinates (associating them with {@code city} for
     * reverse-geocoding / storage), returning the canonical resolved point.
     */
    GeoPoint resolveFromCoordinates(double latitude, double longitude, String city);
}
