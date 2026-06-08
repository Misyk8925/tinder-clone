package com.tinder.profiles.infrastructure.external.location;

import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Location adapter implementing {@link LocationPort} by delegating to
 * {@link LocationServiceClient} (standalone location service with local
 * geocoding fallback, unchanged). Converts the persisted {@link Location} entity
 * the client returns into a domain-friendly {@link ResolvedLocation}
 * (coordinates + canonical city), so the application layer stays free of
 * persistence and transport types.
 */
@Component
@RequiredArgsConstructor
public class LocationResolverAdapter implements LocationPort {

    private final LocationServiceClient locationServiceClient;

    @Override
    public ResolvedLocation resolve(Double latitude, Double longitude, String city) {
        Location location = (latitude != null && longitude != null)
                ? locationServiceClient.resolveFromCoordinates(latitude, longitude, city)
                : locationServiceClient.resolve(city);
        if (location == null) {
            return null;
        }
        GeoPoint position = GeoPoint.of(location.getLatitude(), location.getLongitude()).orElse(null);
        return new ResolvedLocation(position, location.getCity());
    }
}
