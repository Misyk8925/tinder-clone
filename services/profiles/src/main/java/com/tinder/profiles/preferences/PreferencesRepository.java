package com.tinder.profiles.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Atomic upsert: inserts the row only when no matching combination exists.
     * ON CONFLICT DO NOTHING makes this safe under any level of concurrency —
     * the winner inserts, the losers skip — no exception is thrown.
     * A subsequent {@link #findByValues} call will always find the committed row.
     */
    @Modifying
    @Query(value = """
        INSERT INTO preferences (id, min_age, max_age, gender, max_range)
        VALUES (gen_random_uuid(), :minAge, :maxAge, :gender, :maxRange)
        ON CONFLICT ON CONSTRAINT uk_preferences_combination DO NOTHING
        """, nativeQuery = true)
    void upsert(
        @Param("minAge") Integer minAge,
        @Param("maxAge") Integer maxAge,
        @Param("gender") String gender,
        @Param("maxRange") Integer maxRange
    );
}
