package com.tinder.profiles.infrastructure.profile.persistence;

import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.location.LocationRepository;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesDto;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesService;
import com.tinder.profiles.profile.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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
 * carry — Location by city (the row is created earlier as a side effect of the
 * location resolution port) and Preferences via the atomic find-or-create.
 */
@Component
@RequiredArgsConstructor
public class JpaProfileRepositoryAdapter implements ProfileRepositoryPort {

    private final ProfileRepository profileRepository;
    private final ProfilePersistenceMapper mapper;
    private final LocationRepository locationRepository;
    private final PreferencesService preferencesService;

    @Override
    public Optional<Profile> findById(UUID id) {
        return profileRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Profile> findByUserId(String userId) {
        return Optional.ofNullable(profileRepository.findByUserId(userId)).map(mapper::toDomain);
    }

    @Override
    public Optional<Profile> findByName(String name) {
        return Optional.ofNullable(profileRepository.findByName(name)).map(mapper::toDomain);
    }

    @Override
    public Optional<UUID> findActiveProfileIdByUserId(String userId) {
        return Optional.ofNullable(profileRepository.findActiveProfileIdByUserId(userId));
    }

    @Override
    public Page<Profile> findAll(Pageable pageable) {
        return profileRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public List<Profile> findAllById(Collection<UUID> ids) {
        return profileRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
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
     * Resolves the {@link Location} FK row for the aggregate's current city. The
     * row is expected to already exist because the use case resolves the
     * location through {@code LocationPort} (which persists it) before saving.
     * Falls back to the entity's existing location when the aggregate has no
     * city (e.g. premium-only updates that never touch location).
     */
    private Location resolveLocation(Profile profile, com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity) {
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
        if (prefs == null) {
            return entity.getPreferences();
        }
        PreferencesDto dto = new PreferencesDto(
                null, prefs.minAge(), prefs.maxAge(), prefs.gender(), prefs.maxRange());
        return preferencesService.findOrCreate(dto);
    }
}
