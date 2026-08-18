package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the JPA persistence entity
 * ({@link ProfileJpaEntity}) and the pure domain aggregate
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
@Slf4j
@Component
public class ProfilePersistenceMapper {

    /** Maps a JPA entity to the domain aggregate. */
    public Profile toDomain(ProfileJpaEntity entity) {
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
                .hobbies(toDomainHobbies(entity.getHobbies()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Copies the aggregate's scalar state onto an existing or new JPA entity.
     * The caller is responsible for supplying the resolved {@code location} and
     * {@code preferences} FK rows (the aggregate does not carry them).
     */
    public void applyTo(Profile domain,
                        ProfileJpaEntity entity,
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
        entity.setHobbies(toStoredHobbies(domain.getHobbies()));
    }

    /**
     * Stored vocabulary → domain vocabulary. A stored value with no matching
     * {@link Hobby} constant is dropped with a warning: rows outlive the enum, and
     * one retired constant must not make a profile unreadable.
     */
    private List<Hobby> toDomainHobbies(List<com.tinder.contracts.dto.Hobby> stored) {
        if (stored == null) {
            return List.of();
        }
        List<Hobby> hobbies = new ArrayList<>(stored.size());
        for (com.tinder.contracts.dto.Hobby value : stored) {
            try {
                hobbies.add(Hobby.valueOf(value.name()));
            } catch (IllegalArgumentException e) {
                log.warn("Dropping stored hobby '{}' with no domain constant", value);
            }
        }
        return List.copyOf(hobbies);
    }

    /**
     * Domain vocabulary → stored vocabulary, in the mutable list JPA needs.
     * Unlike the read direction this fails loudly: the value came from the domain
     * enum, so a gap here means the two vocabularies have drifted out of sync in
     * the build, not that user data is stale.
     */
    private List<com.tinder.contracts.dto.Hobby> toStoredHobbies(List<Hobby> hobbies) {
        List<com.tinder.contracts.dto.Hobby> stored = new ArrayList<>(hobbies.size());
        for (Hobby hobby : hobbies) {
            try {
                stored.add(com.tinder.contracts.dto.Hobby.valueOf(hobby.name()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Domain hobby '" + hobby + "' has no counterpart in the shared contract enum; "
                                + "the two vocabularies have drifted", e);
            }
        }
        return stored;
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
