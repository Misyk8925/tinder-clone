package com.tinder.profiles.infrastructure.persistence.location;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Local persistence helper for the profiles-owned {@code location} table.
 *
 * <p>The authoritative geocoder is the standalone location service; this class no
 * longer geocodes. It only mirrors already-known coordinates into the local table
 * so that {@code searchByPreferences} can join on {@code location.geo} for distance
 * filtering. It is invoked solely from {@link com.tinder.profiles.infrastructure.external.location.LocationServiceClient}
 * as a degraded fallback when the location service is unavailable: coordinates that
 * the client already holds (GPS input) are persisted as-is — no coordinates are ever
 * invented.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repo;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326

    /**
     * Persists a Location entity from coordinates the caller already holds (e.g. GPS
     * supplied by the client). Performs no geocoding.
     *
     * @param latitude  WGS-84 latitude  (-90  … +90)
     * @param longitude WGS-84 longitude (-180 … +180)
     * @param city      city name supplied by the user (used for the Location.city field)
     */
    public Location createFromCoordinates(double latitude, double longitude, String city) {
        String resolvedCity = city != null && !city.isBlank() ? city : "Unknown";

        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);

        if (!"Unknown".equals(resolvedCity)) {
            return repo.findByCity(resolvedCity).orElseGet(() -> persist(resolvedCity, point, latitude, longitude));
        }
        return persist(resolvedCity, point, latitude, longitude);
    }

    private Location persist(String city, Point point, double latitude, double longitude) {
        Location loc = new Location();
        loc.setCity(city);
        loc.setGeo(point);
        try {
            Location saved = repo.save(loc);
            log.info("Saved GPS-derived location for city '{}': lat={}, lon={}", city, latitude, longitude);
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent insert detected for city '{}', fetching from DB", city);
            return repo.findByCity(city)
                    .orElseThrow(() -> new RuntimeException("Location not found after concurrent insert for city: " + city, e));
        } catch (Exception e) {
            log.error("Error saving GPS location for city '{}': {}", city, e.getMessage(), e);
            throw new RuntimeException("Failed to save GPS location for city: " + city, e);
        }
    }
}
