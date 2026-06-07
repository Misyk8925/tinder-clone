package com.tinder.profiles.infrastructure.profile.location;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.external.location.LocationServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationResolverAdapter")
class LocationResolverAdapterTest {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private LocationServiceClient locationServiceClient;

    private LocationResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocationResolverAdapter(locationServiceClient);
    }

    private static Location location(double lat, double lon) {
        Point p = GEO_FACTORY.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return Location.builder().city("Vienna").geo(p).build();
    }

    @Test
    @DisplayName("resolve converts the resolved Location entity to a domain GeoPoint")
    void resolveByCity() {
        given(locationServiceClient.resolve("Vienna")).willReturn(location(48.2, 16.37));

        GeoPoint point = adapter.resolve("Vienna");

        then(point).isEqualTo(new GeoPoint(48.2, 16.37));
    }

    @Test
    @DisplayName("resolveFromCoordinates converts the resolved Location entity to a domain GeoPoint")
    void resolveByCoordinates() {
        given(locationServiceClient.resolveFromCoordinates(52.52, 13.40, "Berlin"))
                .willReturn(location(52.52, 13.40));

        GeoPoint point = adapter.resolveFromCoordinates(52.52, 13.40, "Berlin");

        then(point).isEqualTo(new GeoPoint(52.52, 13.40));
    }

    @Test
    @DisplayName("returns null when the client yields no location")
    void nullLocation() {
        given(locationServiceClient.resolve("Nowhere")).willReturn(null);

        then(adapter.resolve("Nowhere")).isNull();
    }
}
