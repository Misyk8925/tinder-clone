package com.tinder.profiles.infrastructure.external.location;

import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.external.location.LocationServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Location adapter implementing {@link LocationPort} by delegating to
 * {@link LocationServiceClient} (standalone location service with local
 * geocoding fallback, unchanged). Converts the persisted {@link Location} entity
 * the client returns into a domain {@link GeoPoint}, so the application layer
 * stays free of persistence and transport types.
 */
@Component
@RequiredArgsConstructor
public class LocationResolverAdapter implements LocationPort {

    private final LocationServiceClient locationServiceClient;

    @Override
    public GeoPoint resolve(String city) {
        return toGeoPoint(locationServiceClient.resolve(city));
    }

    @Override
    public GeoPoint resolveFromCoordinates(double latitude, double longitude, String city) {
        return toGeoPoint(locationServiceClient.resolveFromCoordinates(latitude, longitude, city));
    }

    private GeoPoint toGeoPoint(Location location) {
        if (location == null) {
            return null;
        }
        return GeoPoint.of(location.getLatitude(), location.getLongitude()).orElse(null);
    }
}
