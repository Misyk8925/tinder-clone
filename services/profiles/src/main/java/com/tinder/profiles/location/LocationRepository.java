package com.tinder.profiles.location;



import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends org.springframework.data.jpa.repository.JpaRepository<Location, UUID> {

    /**
     * Find location by city name
     * @param city the city name
     * @return Optional containing the location if found
     */
    Optional<Location> findByCity(String city);
}
