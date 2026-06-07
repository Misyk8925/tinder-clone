package com.tinder.profiles.domain.profile;

import java.util.Optional;

/**
 * Immutable geographic coordinate (WGS-84) and the pure distance math over it.
 *
 * <p>A domain value object: no identity, no framework dependencies, no
 * persistence. The Haversine implementation here is the single authoritative
 * great-circle formula for the {@code profiles} domain and is the function
 * earmarked for extraction into the shared {@code tinder-geo} library
 * (service-refactor-plan §5.4); keeping it dependency-free makes that a
 * straight move.
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Builds a {@link GeoPoint} only when both coordinates are present;
     * {@link Optional#empty()} otherwise, so callers can treat "no known
     * position" uniformly.
     */
    public static Optional<GeoPoint> of(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(latitude, longitude));
    }

    /** Great-circle distance to {@code other} in kilometres (Haversine). */
    public double distanceKm(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
