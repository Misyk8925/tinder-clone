package com.tinder.profiles.profile;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Profile findByName(String username);
    Profile findByUserId(String userId);

    List<Profile> findAllByIsDeletedFalse();

    /**
     * Search profiles by age and gender preferences.
     * Filters out deleted profiles and the viewer themselves.
     * Uses database-level filtering for efficiency.
     */
    @Query("""
        SELECT p FROM Profile p
        WHERE p.isDeleted = false
        AND p.profileId != :viewerId
        AND (:minAge IS NULL OR p.age >= :minAge)
        AND (:maxAge IS NULL OR p.age <= :maxAge)
        AND (:gender IS NULL OR :gender = 'any' OR :gender = 'all'
             OR LOWER(p.gender) = LOWER(:gender))
        ORDER BY p.createdAt DESC
        """)
    List<Profile> searchByPreferences(
        @Param("viewerId") UUID viewerId,
        @Param("minAge") Integer minAge,
        @Param("maxAge") Integer maxAge,
        @Param("gender") String gender,
        Pageable pageable
    );
}


