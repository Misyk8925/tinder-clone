package com.tinder.profiles.location;

import com.tinder.profiles.geocoding.NominatimService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repo;

    private final NominatimService geocodingService;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326

    @Transactional
    public Location create(String city) {
        System.out.println(city);
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City cannot be null or blank");
        }
        Optional<NominatimService.GeoPoint> geocoded = null;
        try {
            geocoded = geocodingService.geocodeCity(city);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        var loc = new Location();

        if (geocoded.isPresent()) {
            Point point = geometryFactory.createPoint(new Coordinate(geocoded.get().lat(), geocoded.get().lon()));
            point.setSRID(4326);
            loc.setGeo(point);
            loc.setCity(city);
        }

        try {
            return repo.save(loc);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }
}