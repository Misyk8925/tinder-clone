package com.tinder.profiles.profile;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Profile findByName(String username);
    Profile findByUserId(String userId);

    @Query("""
        SELECT p.profileId FROM Profile p
        WHERE p.userId = :userId
          AND p.isDeleted = false
        """)
    UUID findActiveProfileIdByUserId(@Param("userId") String userId);

    List<Profile> findAllByIsDeletedFalse();

    /**
     * Find all premium profiles whose subscription has expired.
     */
    List<Profile> findAllByIsPremiumTrueAndPremiumExpiresAtBefore(LocalDateTime now);

    /**
     * Find all soft-deleted profiles that were deleted before the given cutoff date.
     * Used by the cleanup scheduler to permanently purge stale records.
     */
    List<Profile> findAllByIsDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);

    /**
     * Search profiles by age and gender preferences.
     * Filters out deleted profiles and the viewer themselves.
     * Uses database-level filtering for efficiency.
     */
    @Query("""
        SELECT DISTINCT p FROM Profile p
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

    @Query(value = """
        SELECT
            p.id,
            p.name,
            p.age,
            p.bio,
            p.city,
            p.is_active,
            p.is_deleted,
            l.id,
            ST_Y(l.geo::geometry),
            ST_X(l.geo::geometry),
            l.city,
            l.created_at,
            l.updated_at,
            pref.min_age,
            pref.max_age,
            pref.gender,
            pref.max_range
        FROM profiles p
        JOIN location l ON l.id = p.location_id
        JOIN preferences pref ON pref.id = p.preferences_id
        WHERE p.is_deleted = false
          AND p.id IN (:ids)
        """, nativeQuery = true)
    List<Object[]> findSharedProfileRowsByIds(@Param("ids") Collection<UUID> ids);

    @Query(value = """
        SELECT
            p.id,
            p.name,
            p.age,
            p.bio,
            p.city,
            p.is_active,
            p.is_deleted,
            l.id,
            ST_Y(l.geo::geometry),
            ST_X(l.geo::geometry),
            l.city,
            l.created_at,
            l.updated_at,
            pref.min_age,
            pref.max_age,
            pref.gender,
            pref.max_range
        FROM profiles p
        JOIN location l ON l.id = p.location_id
        JOIN preferences pref ON pref.id = p.preferences_id
        WHERE p.is_deleted = false
          AND p.id <> :viewerId
          AND (:minAge IS NULL OR p.age >= :minAge)
          AND (:maxAge IS NULL OR p.age <= :maxAge)
          AND (:gender IS NULL OR :gender = 'any' OR :gender = 'all'
               OR LOWER(p.gender) = LOWER(:gender))
        ORDER BY p.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchSharedProfileRowsByPreferences(
        @Param("viewerId") UUID viewerId,
        @Param("minAge") Integer minAge,
        @Param("maxAge") Integer maxAge,
        @Param("gender") String gender,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT
            p.id,
            p.name,
            p.age,
            p.gender,
            p.bio,
            p.city,
            ph.photo_id,
            ph.url,
            ph.position,
            ph.is_primary,
            h.hobby
        FROM profiles p
        LEFT JOIN photo ph ON ph.profile_id = p.id
        LEFT JOIN profile_hobbies h ON h.profile_id = p.id
        WHERE p.is_deleted = false
          AND p.id IN (:ids)
        ORDER BY p.id, ph.position ASC NULLS LAST
        """, nativeQuery = true)
    List<Object[]> findDeckProfileRowsByIds(@Param("ids") Collection<UUID> ids);

    @Query(value = """
        WITH candidates AS (
            SELECT p.id, p.created_at
            FROM profiles p
            WHERE p.is_deleted = false
              AND p.id <> :viewerId
              AND (:minAge IS NULL OR p.age >= :minAge)
              AND (:maxAge IS NULL OR p.age <= :maxAge)
              AND (:gender IS NULL OR :gender = 'any' OR :gender = 'all'
                   OR LOWER(p.gender) = LOWER(:gender))
            ORDER BY p.created_at DESC
            LIMIT :limit
        )
        SELECT
            p.id,
            p.name,
            p.age,
            p.gender,
            p.bio,
            p.city,
            ph.photo_id,
            ph.url,
            ph.position,
            ph.is_primary,
            h.hobby
        FROM profiles p
        JOIN candidates c ON c.id = p.id
        LEFT JOIN photo ph ON ph.profile_id = p.id
        LEFT JOIN profile_hobbies h ON h.profile_id = p.id
        ORDER BY c.created_at DESC, ph.position ASC NULLS LAST
        """, nativeQuery = true)
    List<Object[]> searchDeckProfileRowsByPreferences(
        @Param("viewerId") UUID viewerId,
        @Param("minAge") Integer minAge,
        @Param("maxAge") Integer maxAge,
        @Param("gender") String gender,
        @Param("limit") int limit
    );
}
