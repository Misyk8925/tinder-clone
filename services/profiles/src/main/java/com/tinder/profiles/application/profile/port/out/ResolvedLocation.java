package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.GeoPoint;

import java.util.UUID;

/**
 * The canonical result of resolving a location: the coordinates plus the
 * authoritative city name the location store recorded. The city is returned (not
 * just echoed back) because geocoding may normalise it. {@code locationId} is the
 * local location row already persisted by the location port for this write —
 * required because city {@code Unknown} is not a unique key (each GPS-only fix
 * is its own row). Named cities still share one row.
 */
public record ResolvedLocation(GeoPoint position, String city, UUID locationId) {

    public ResolvedLocation(GeoPoint position, String city) {
        this(position, city, null);
    }
}
