package com.tinder.profiles.location;

import com.tinder.profiles.geocoding.NominatimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repo;

    private final NominatimService geocodingService;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326

    /** L1: In-memory cache — avoids ALL DB roundtrips for already-resolved cities. */
    private final ConcurrentHashMap<String, Location> locationCache = new ConcurrentHashMap<>();

    /**
     * Per-city locks: ensures only one thread geocodes + inserts a new city row.
     * All other threads for the same city wait and then get the cached result.
     */
    private final ConcurrentHashMap<String, ReentrantLock> cityLocks = new ConcurrentHashMap<>();

    public Location create(String city) {

        if (city == null || city.isBlank()) {
            log.error("Attempted to create location with null or blank city");
            throw new IllegalArgumentException("City must not be null or blank");
        }

        // L1: fast path — no lock, no DB call
        Location cached = locationCache.get(city);
        if (cached != null) {
            log.debug("Location L1 cache hit for city '{}'", city);
            return cached;
        }

        // Serialize per-city: only one thread does geocoding+insert, the rest wait
        ReentrantLock lock = cityLocks.computeIfAbsent(city, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock — another thread may have populated cache
            Location afterLock = locationCache.get(city);
            if (afterLock != null) {
                log.debug("Location L1 cache hit for city '{}' (after lock)", city);
                return afterLock;
            }

            // L2: Database lookup (only one thread reaches here per city)
            Optional<Location> existingLocation = repo.findByCity(city);
            if (existingLocation.isPresent()) {
                log.debug("Found existing location for city '{}' in DB, caching it", city);
                locationCache.put(city, existingLocation.get());
                return existingLocation.get();
            }

            // L3: Geocode and insert new city row
            return geocodeAndSave(city);

        } finally {
            lock.unlock();
        }
    }

    // No @Transactional here — repo.save() is itself transactional; self-invocation
    // would bypass Spring's proxy anyway, so we simply rely on the repository's own transaction.
    private Location geocodeAndSave(String city) {
        Optional<NominatimService.GeoPoint> geocoded = Optional.empty();
        try {
            geocoded = geocodingService.geocodeCity(city);
        } catch (Exception e) {
            log.warn("Geocoding failed for city '{}': {} - will use default coordinates", city, e.getMessage());
        }

        Location loc = new Location();
        loc.setCity(city);

        if (geocoded.isPresent()) {
            Point point = geometryFactory.createPoint(
                    new Coordinate(geocoded.get().lat(), geocoded.get().lon()));
            point.setSRID(4326);
            loc.setGeo(point);
            log.info("Geocoded city '{}': lat={}, lon={}", city, geocoded.get().lat(), geocoded.get().lon());
        } else {
            // Fallback: center of Europe when geocoding unavailable
            log.warn("Geocoding unavailable for '{}', using default coordinates", city);
            Point defaultPoint = geometryFactory.createPoint(new Coordinate(50.0, 10.0));
            defaultPoint.setSRID(4326);
            loc.setGeo(defaultPoint);
        }

        try {
            Location savedLocation = repo.save(loc);
            locationCache.put(city, savedLocation);
            log.debug("Saved new location for city '{}'", city);
            return savedLocation;
        } catch (DataIntegrityViolationException e) {
            // Another node/transaction inserted the row concurrently; fetch it
            log.warn("Concurrent insert detected for city '{}', fetching from DB", city);
            Location existing = repo.findByCity(city)
                    .orElseThrow(() -> new RuntimeException("Location not found after concurrent insert for city: " + city));
            locationCache.put(city, existing);
            return existing;
        } catch (Exception e) {
            log.error("Error saving location for city '{}': {}", city, e.getMessage(), e);
            throw new RuntimeException("Failed to save location for city: " + city, e);
        }
    }
}
