package com.tinder.profiles.api.profile;

import com.tinder.profiles.AbstractPostgresIntegrationTest;
import com.tinder.profiles.TestJwtSecurityConfig;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.location.LocationRepository;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtSecurityConfig.class)
class GpsOnlyProfileCreateDuplicateUnknownIT extends AbstractPostgresIntegrationTest {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String USER_ID = "gps-unknown-create-user";
    private static final double GPS_LAT = 52.52;
    private static final double GPS_LON = 13.40;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void seedDuplicateUnknownLocations() {
        profileRepository.deleteAll();
        locationRepository.deleteAll();
        for (int i = 0; i < 6; i++) {
            locationRepository.save(unknownAt(50.0 + (i * 0.01), 10.0));
        }
    }

    @Test
    @DisplayName("Given several Unknown location rows, When creating a GPS-only profile, Then it is created at the submitted coordinates")
    void givenDuplicateUnknownCities_whenCreateWithGpsAndNoCity_thenCreatedAtSubmittedCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/profiles")
                        .content("""
                                {
                                    "name": "Alex",
                                    "age": 28,
                                    "gender": "male",
                                    "bio": "gps only",
                                    "preferences": {
                                        "minAge": 20,
                                        "maxAge": 40,
                                        "gender": "female",
                                        "maxRange": 50
                                    },
                                    "latitude": 52.52,
                                    "longitude": 13.40
                                }
                                """)
                        .header("Authorization", TestJwtSecurityConfig.bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        ProfileJpaEntity saved = profileRepository.findByUserId(USER_ID);
        then(saved).isNotNull();
        then(saved.getCity()).isEqualTo("Unknown");
        then(saved.getLocation().getLatitude()).isEqualTo(GPS_LAT);
        then(saved.getLocation().getLongitude()).isEqualTo(GPS_LON);
        then(saved.getLocation().getId()).isNotIn(
                locationRepository.findAll().stream()
                        .filter(location -> location.getLatitude() != null && location.getLatitude() < 51.0)
                        .map(Location::getId)
                        .toList());
    }

    private static Location unknownAt(double latitude, double longitude) {
        Point point = GEO_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return Location.builder().city("Unknown").geo(point).build();
    }
}
