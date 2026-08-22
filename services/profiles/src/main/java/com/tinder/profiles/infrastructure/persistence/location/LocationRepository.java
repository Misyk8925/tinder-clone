package com.tinder.profiles.infrastructure.persistence.location;



import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends org.springframework.data.jpa.repository.JpaRepository<Location, UUID> {

    /**
     * Named cities are intended to share one row. {@code Unknown} is not unique
     * (one row per GPS-only fix), so this must not use Hibernate's unique-result
     * query — it returns the oldest matching row when duplicates exist.
     */
    Optional<Location> findFirstByCityOrderByIdAsc(String city);

    default Optional<Location> findByCity(String city) {
        return findFirstByCityOrderByIdAsc(city);
    }
}
