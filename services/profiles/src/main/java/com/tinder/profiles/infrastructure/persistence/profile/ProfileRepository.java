package com.tinder.profiles.infrastructure.persistence.profile;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<ProfileJpaEntity, UUID> {
    ProfileJpaEntity findByName(String username);
    ProfileJpaEntity findByUserId(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Profile p
        SET p.version = p.version + 1,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.profileId = :profileId
        """)
    int incrementAggregateVersion(@Param("profileId") UUID profileId);

    @Query(value = """
        SELECT p.id FROM profiles p
        WHERE (:afterProfileId IS NULL OR p.id > :afterProfileId)
        ORDER BY p.id
        """, nativeQuery = true)
    List<UUID> findNextProjectionBackfillIds(
            @Param("afterProfileId") UUID afterProfileId,
            Pageable pageable
    );

    @Query("""
        SELECT p.profileId FROM Profile p
        WHERE p.userId = :userId
          AND p.isDeleted = false
        """)
    UUID findActiveProfileIdByUserId(@Param("userId") String userId);

    List<ProfileJpaEntity> findAllByIsDeletedFalse();

    /**
     * Find all premium profiles whose subscription has expired.
     */
    List<ProfileJpaEntity> findAllByIsPremiumTrueAndPremiumExpiresAtBefore(LocalDateTime now);

    /**
     * Find all soft-deleted profiles that were deleted before the given cutoff date.
     * Used by the cleanup scheduler to permanently purge stale records.
     */
    List<ProfileJpaEntity> findAllByIsDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);

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
    List<ProfileJpaEntity> searchByPreferences(
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

    /**
     * Batch-loads hobbies for several profiles as {@code (profile_id, hobby)} rows.
     * The two projections above are flat, so they cannot carry the
     * {@code profile_hobbies} element collection; callers stitch it back in via
     * {@code SharedProfileRowMapper.hobbiesByProfileId}.
     */
    @Query(value = """
        SELECT
            ph.profile_id,
            ph.hobby
        FROM profile_hobbies ph
        WHERE ph.profile_id IN (:ids)
        """, nativeQuery = true)
    List<Object[]> findHobbyRowsByProfileIds(@Param("ids") Collection<UUID> ids);
}
