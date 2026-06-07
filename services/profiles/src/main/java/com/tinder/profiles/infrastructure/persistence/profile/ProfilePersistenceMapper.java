package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import org.springframework.stereotype.Component;

/**
 * Translates between the JPA persistence entity
 * ({@link com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity}) and the pure domain aggregate
 * ({@link Profile}).
 *
 * <p>The JPA→domain direction is total and stateless. The domain→JPA direction
 * is intentionally <em>not</em> a full constructor here: the aggregate carries
 * resolved coordinates ({@link GeoPoint}) and {@link MatchingPreferences} rather
 * than the {@link Location}/{@link Preferences} FK rows the entity requires, so
 * the {@link JpaProfileRepositoryAdapter} reconciles those FKs and then calls
 * {@link #applyTo} to copy the scalar fields onto a (possibly already-loaded)
 * entity. This preserves persistence-only state (version, photos, createdAt).
 */
@Component
public class ProfilePersistenceMapper {

    /** Maps a JPA entity to the domain aggregate. */
    public Profile toDomain(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Profile.builder()
                .id(entity.getProfileId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .age(entity.getAge())
                .gender(entity.getGender())
                .bio(entity.getBio())
                .city(entity.getCity())
                .position(toGeoPoint(entity.getLocation()))
                .active(entity.isActive())
                .premium(entity.isPremium())
                .premiumExpiresAt(entity.getPremiumExpiresAt())
                .deleted(entity.isDeleted())
                .deletedAt(entity.getDeletedAt())
                .preferences(toMatchingPreferences(entity.getPreferences()))
                .hobbies(entity.getHobbies())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Copies the aggregate's scalar state onto an existing or new JPA entity.
     * The caller is responsible for supplying the resolved {@code location} and
     * {@code preferences} FK rows (the aggregate does not carry them).
     */
    public void applyTo(Profile domain,
                        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity,
                        Location location,
                        Preferences preferences) {
        entity.setUserId(domain.getUserId());
        entity.setName(domain.getName());
        entity.setAge(domain.getAge());
        entity.setGender(domain.getGender());
        entity.setBio(domain.getBio());
        entity.setCity(domain.getCity());
        entity.setLocation(location);
        entity.setPreferences(preferences);
        entity.setActive(domain.isActive());
        entity.setPremium(domain.isPremium());
        entity.setPremiumExpiresAt(domain.getPremiumExpiresAt());
        entity.setDeleted(domain.isDeleted());
        entity.setDeletedAt(domain.getDeletedAt());
        entity.setHobbies(new java.util.ArrayList<>(domain.getHobbies()));
    }

    private GeoPoint toGeoPoint(Location location) {
        if (location == null) {
            return null;
        }
        return GeoPoint.of(location.getLatitude(), location.getLongitude()).orElse(null);
    }

    private MatchingPreferences toMatchingPreferences(Preferences preferences) {
        if (preferences == null) {
            return null;
        }
        return new MatchingPreferences(
                preferences.getMinAge(),
                preferences.getMaxAge(),
                preferences.getGender(),
                preferences.getMaxRange());
    }
}
