package com.tinder.profiles.geocoding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for NominatimService
 * Tests geocoding functionality with real API calls
 */
@SpringBootTest
@ActiveProfiles("test")
class NominatimServiceIntegrationTest {

    @Autowired
    private NominatimService nominatimService;

    @Test
    void testGeocodeValidCity_Berlin() {
        // Given
        String city = "Berlin";

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertTrue(result.isPresent(), "Berlin should be found");
        assertNotNull(result.get());
        assertTrue(result.get().lat() > 52.0 && result.get().lat() < 53.0,
            "Berlin latitude should be around 52.5");
        assertTrue(result.get().lon() > 13.0 && result.get().lon() < 14.0,
            "Berlin longitude should be around 13.4");
    }

    @Test
    void testGeocodeValidCity_Vienna() {
        // Given
        String city = "Vienna";

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertTrue(result.isPresent(), "Vienna should be found");
        assertNotNull(result.get());
        assertTrue(result.get().lat() > 48.0 && result.get().lat() < 49.0,
            "Vienna latitude should be around 48.2");
        assertTrue(result.get().lon() > 16.0 && result.get().lon() < 17.0,
            "Vienna longitude should be around 16.3");
    }

    @Test
    void testGeocodeInvalidCity() {
        // Given
        String city = "ThisCityDoesNotExist123456789";

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertFalse(result.isPresent(), "Invalid city should not be found");
    }

    @Test
    void testGeocodeNullCity() {
        // Given
        String city = null;

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertFalse(result.isPresent(), "Null city should return empty");
    }

    @Test
    void testGeocodeBlankCity() {
        // Given
        String city = "   ";

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertFalse(result.isPresent(), "Blank city should return empty");
    }

    @Test
    void testGeocodeCityWithWhitespace() {
        // Given
        String city = "  Munich  ";

        // When
        Optional<NominatimService.GeoPoint> result = nominatimService.geocodeCity(city);

        // Then
        assertTrue(result.isPresent(), "Munich with whitespace should be found");
        assertNotNull(result.get());
        assertTrue(result.get().lat() > 48.0 && result.get().lat() < 49.0,
            "Munich latitude should be around 48.1");
        assertTrue(result.get().lon() > 11.0 && result.get().lon() < 12.0,
            "Munich longitude should be around 11.5");
    }
}

