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
        Optional<NominatimService.GeoPoint> geocoded = Optional.empty();

        try {
            geocoded = geocodingService.geocodeCity(city);
        } catch (Exception e) {
            log.error("Geocoding error for city '{}': {}", city, e.getMessage(), e);
        }

        if (geocoded.isEmpty()) {
            log.error("City not found or could not be geocoded: '{}'", city);
            throw new IllegalArgumentException("City not found: " + city);
        }

        var loc = new Location();
        Point point = geometryFactory.createPoint(new Coordinate(geocoded.get().lat(), geocoded.get().lon()));
        point.setSRID(4326);
        loc.setGeo(point);
        loc.setCity(city);

        try {
            Location savedLocation = repo.save(loc);
            log.info("Successfully created location for city '{}': lat={}, lon={}",
                city, geocoded.get().lat(), geocoded.get().lon());
            return savedLocation;
        } catch (Exception e) {
            log.error("Error saving location for city '{}': {}", city, e.getMessage(), e);
            throw new RuntimeException("Failed to save location for city: " + city, e);
        }
    }
}