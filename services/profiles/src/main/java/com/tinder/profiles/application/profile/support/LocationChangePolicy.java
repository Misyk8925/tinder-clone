package com.tinder.profiles.application.profile.support;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Profile;

/**
 * How far a profile must move before its coordinates are re-resolved and an
 * update event is emitted. Suppresses GPS jitter.
 *
 * <p>A plain value object so the use cases stay framework-free: the threshold is
 * bound from configuration in {@code config.application.ProfileApplicationConfig}.
 */
public record LocationChangePolicy(double thresholdKm) {

    public boolean movedSignificantly(Profile profile, GeoPoint to) {
        return profile.hasMovedBeyond(to, thresholdKm);
    }
}
