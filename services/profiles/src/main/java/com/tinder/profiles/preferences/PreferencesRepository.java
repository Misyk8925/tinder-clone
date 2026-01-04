package com.tinder.profiles.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepository extends JpaRepository<Preferences, UUID> {

    @Query("""
        SELECT p FROM Preferences p
        WHERE (:minAge IS NULL AND p.minAge IS NULL OR p.minAge = :minAge)
        AND (:maxAge IS NULL AND p.maxAge IS NULL OR p.maxAge = :maxAge)
        AND (:gender IS NULL AND p.gender IS NULL OR p.gender = :gender)
        AND p.maxRange = :maxRange
        """)
    Optional<Preferences> findByValues(
        @Param("minAge") Integer minAge,
        @Param("maxAge") Integer maxAge,
        @Param("gender") String gender,
        @Param("maxRange") Integer maxRange
    );
}
