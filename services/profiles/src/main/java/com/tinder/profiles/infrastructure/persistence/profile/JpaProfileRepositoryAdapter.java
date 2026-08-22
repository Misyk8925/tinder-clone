package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.location.LocationRepository;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesService;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing {@link ProfileRepositoryPort} on top of the
 * existing Spring Data {@link ProfileRepository} (which is left unchanged).
 *
 * <p>Read paths map JPA→domain via {@link ProfilePersistenceMapper}. The
 * {@link #save} path uses <em>load-and-reconcile</em>: it preserves
 * persistence-only state (version, photos, createdAt) and resolves the non-null
 * {@code location_id}/{@code preferences_id} FKs that the aggregate does not
 * own as entities — Location by the id returned from {@code LocationPort}
 * (falling back to city for writes that never resolved), and Preferences via
 * the atomic find-or-create.
 */
@Component
@RequiredArgsConstructor
public class JpaProfileRepositoryAdapter implements ProfileRepositoryPort {

    private final ProfileRepository profileRepository;
    private final ProfilePersistenceMapper mapper;
    private final LocationRepository locationRepository;
    private final PreferencesService preferencesService;

    @Override
    public Optional<Profile> findByUserId(String userId) {
        return Optional.ofNullable(profileRepository.findByUserId(userId)).map(mapper::toDomain);
    }

    @Override
    public List<Profile> findAllById(Collection<UUID> ids) {
        return profileRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Profile> findExpiredPremium(LocalDateTime asOf) {
        return profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(asOf).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findSoftDeletedBefore(LocalDateTime cutoff) {
        return profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(cutoff).stream()
                .map(ProfileJpaEntity::getProfileId)
                .toList();
    }

    @Override
    public Profile save(Profile profile) {
        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = profile.getId() != null
                ? profileRepository.findById(profile.getId())
                        .orElseGet(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity::new)
                : new com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity();

        Location location = resolveLocation(profile, entity);
        Preferences preferences = resolvePreferences(profile, entity);

        mapper.applyTo(profile, entity, location, preferences);

        return mapper.toDomain(profileRepository.save(entity));
    }

    @Override
    public void deleteAllById(Collection<UUID> ids) {
        profileRepository.deleteAllById(ids);
    }

    /**
     * Resolves the {@link Location} FK row. Prefer the id returned by
     * {@code LocationPort} for this write — city {@code Unknown} is not unique.
     * Named-city writes still fall back to lookup by city. Premium-only updates
     * that never touch location keep the entity's existing row.
     */
    private Location resolveLocation(Profile profile, com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity) {
        if (profile.getLocationId() != null) {
            return locationRepository.findById(profile.getLocationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No persisted Location for id '" + profile.getLocationId()
                                    + "'. Resolve the location via LocationPort before saving the profile."));
        }
        String city = profile.getCity();
        if (city == null || city.isBlank()) {
            return entity.getLocation();
        }
        return locationRepository.findByCity(city)
                .orElseThrow(() -> new IllegalStateException(
                        "No persisted Location for city '" + city + "'. Resolve the location via "
                                + "LocationPort before saving the profile."));
    }

    private Preferences resolvePreferences(Profile profile, com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity) {
        MatchingPreferences prefs = profile.getPreferences();
        return prefs == null ? entity.getPreferences() : preferencesService.findOrCreate(prefs);
    }
}
