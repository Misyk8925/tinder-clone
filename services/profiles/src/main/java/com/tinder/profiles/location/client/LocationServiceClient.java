package com.tinder.profiles.location.client;

import com.tinder.profiles.location.Location;
import com.tinder.profiles.location.LocationRepository;
import com.tinder.profiles.location.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Primary location resolver. Delegates to the standalone location-go service.
 * Falls back to the local {@link LocationService} when the remote service is
 * unavailable (timeout, 5xx, circuit open).
 */
@Service
@Slf4j
public class LocationServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final GeometryFactory GEO_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final WebClient locationWebClient;
    private final LocationService locationService;
    private final LocationRepository locationRepository;

    public LocationServiceClient(
            @Qualifier("locationWebClient") WebClient locationWebClient,
            LocationService locationService,
            LocationRepository locationRepository
    ) {
        this.locationWebClient = locationWebClient;
        this.locationService = locationService;
        this.locationRepository = locationRepository;
    }

    public Location resolve(String city) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("city", city);

            RemoteLocationResponse resp = locationWebClient.post()
                    .uri("/api/v1/locations/resolve")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RemoteLocationResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (resp != null) {
                log.debug("Location service resolved city '{}' → id={}", city, resp.id());
                return findOrSaveLocally(resp);
            }
        } catch (Exception e) {
            log.warn("Location service unavailable for city '{}', falling back to local: {}", city, e.getMessage());
        }

        return locationService.create(city);
    }

    public Location resolveFromCoordinates(double latitude, double longitude, String city) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("city", city);
            body.put("latitude", latitude);
            body.put("longitude", longitude);

            RemoteLocationResponse resp = locationWebClient.post()
                    .uri("/api/v1/locations/resolve")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RemoteLocationResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (resp != null) {
                log.debug("Location service resolved coords ({},{}) → id={}", latitude, longitude, resp.id());
                return findOrSaveLocally(resp);
            }
        } catch (Exception e) {
            log.warn("Location service unavailable for coords ({},{}), falling back to local: {}",
                    latitude, longitude, e.getMessage());
        }

        return locationService.createFromCoordinates(latitude, longitude, city);
    }

    private Location findOrSaveLocally(RemoteLocationResponse resp) {
        String city = resp.city() != null ? resp.city() : "Unknown";

        return locationRepository.findByCity(city).orElseGet(() -> {
            Point point = GEO_FACTORY.createPoint(new Coordinate(resp.longitude(), resp.latitude()));
            point.setSRID(4326);

            Location loc = new Location();
            loc.setCity(city);
            loc.setGeo(point);
            return locationRepository.save(loc);
        });
    }

    record RemoteLocationResponse(java.util.UUID id, String city, Double latitude, Double longitude) {}
}
