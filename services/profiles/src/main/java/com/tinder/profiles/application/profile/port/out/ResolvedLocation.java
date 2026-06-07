package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.GeoPoint;

/**
 * The canonical result of resolving a location: the coordinates plus the
 * authoritative city name the location store recorded. The city is returned (not
 * just echoed back) because geocoding may normalise it, and the persistence
 * adapter reconciles the {@code location_id} FK by that city — so the aggregate
 * must carry the same value.
 */
public record ResolvedLocation(GeoPoint position, String city) {
}
