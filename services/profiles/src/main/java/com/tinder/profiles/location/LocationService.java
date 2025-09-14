package com.tinder.profiles.location;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repo;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326

    public Location create(String city, double lon, double lat) {
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        point.setSRID(4326);

        Location loc = Location.builder()
                .geo(point)
                .build();

        return repo.save(loc);
    }
}