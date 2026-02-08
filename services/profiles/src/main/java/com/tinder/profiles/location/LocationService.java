package com.tinder.profiles.location;

import com.tinder.profiles.geocoding.NominatimService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repo;

    private final NominatimService geocodingService;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326

    @Transactional
    public Location create(String city) {

        if (city == null || city.isBlank()) {
            log.error("Attempted to create location with null or blank city");
            throw new IllegalArgumentException("City must not be null or blank");
        }

        log.debug("Creating location for city: '{}'", city);

        // Check if location already exists in database
        Optional<Location> existingLocation = repo.findByCity(city);
        if (existingLocation.isPresent()) {
            log.info("Found existing location for city '{}', reusing it", city);
            return existingLocation.get();
        }

        Optional<NominatimService.GeoPoint> geocoded = Optional.empty();

        try {
            geocoded = geocodingService.geocodeCity(city);
        } catch (Exception e) {
            log.warn("Geocoding failed for city '{}': {} - will use default coordinates",
                    city, e.getMessage());
        }

        Location loc = new Location();

        if (geocoded.isPresent()) {
            // Use geocoded coordinates
            Point point = geometryFactory.createPoint(
                    new Coordinate(geocoded.get().lat(), geocoded.get().lon()));
            point.setSRID(4326);
            loc.setGeo(point);
            log.info("Successfully created location for city '{}': lat={}, lon={}",
                    city, geocoded.get().lat(), geocoded.get().lon());
        } else {
            // Fallback: use default coordinates (center of Europe) when geocoding unavailable
            log.warn("Geocoding unavailable for '{}', using default coordinates (Circuit Breaker may be open)", city);
            Point defaultPoint = geometryFactory.createPoint(new Coordinate(50.0, 10.0)); // Center of Europe
            defaultPoint.setSRID(4326);
            loc.setGeo(defaultPoint);
            log.info("Created location for city '{}' with default coordinates (fallback)", city);
        }

        loc.setCity(city);

        try {
            Location savedLocation = repo.save(loc);
            return savedLocation;
        } catch (Exception e) {
            log.error("Error saving location for city '{}': {}", city, e.getMessage(), e);
            throw new RuntimeException("Failed to save location for city: " + city, e);
        }
    }
}