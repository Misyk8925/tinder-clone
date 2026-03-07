package com.tinder.profiles.profile;

import com.tinder.profiles.kafka.dto.ChangeType;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import com.tinder.profiles.outbox.ProfileOutboxService;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.preferences.PreferencesService;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.profile.exception.ProfileAlreadyExistsException;
import com.tinder.profiles.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.profile.exception.PatchOperationException;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.redis.ResilientCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileApplicationService {

    private final ProfileRepository profileRepository;
    private final PreferencesRepository preferencesRepository;
    private final ProfileDomainService domainService;
    private final CreateProfileMapper createMapper;
    private final GetProfileMapper getMapper;
    private final ResilientCacheManager resilientCacheManager;
    private final PreferencesService preferencesService;
    private final ProfileOutboxService profileOutboxService;


    private static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";


    public Page<Profile> getAll(Pageable pageable) {
        return profileRepository.findAll(pageable);
    }

    public GetProfileDto getOne(UUID id) {
        try {
            Cache.ValueWrapper profileCache = resilientCacheManager.get(PROFILE_CACHE_NAME, id);

            if (profileCache != null) {
                Object cached = profileCache.get();

                if (cached instanceof Profile profile) {
                    if (profile.isDeleted()) {
                        return null;
                    }
                    return getMapper.toGetProfileDto(profile);
                } else {
                    String cachedType = (cached != null) ? cached.getClass().getName() : "null";
                    log.warn("Invalid object type in cache for key {}: {}", id, cachedType);
                }
            }

            Optional<Profile> profileOptional = profileRepository.findById(id);
            if (profileOptional.isEmpty()) {
                throw new ProfileNotFoundException(id.toString(), "id");
            }

            Profile profile = profileOptional.get();
            if (profile.isDeleted()) {
                return null;
            }

            putInCache(id, profile);
            return getMapper.toGetProfileDto(profile);

        } catch (Exception e) {
            log.error("Error retrieving profile {}: {}. Loading from database.", id, e.getMessage());
            Optional<Profile> profileOptional = profileRepository.findById(id);
            if (profileOptional.isEmpty()) {
                throw new ProfileNotFoundException(id.toString(), "id");
            }
            Profile profile = profileOptional.get();
            return profile.isDeleted() ? null : getMapper.toGetProfileDto(profile);
        }
    }

    public Profile getByUsername(String username) {
        return profileRepository.findByName(username);
    }

    public Profile getByUserId(String userId) {
        return profileRepository.findByUserId(userId);
    }

    @Transactional
    public Profile create(CreateProfileDtoV1 profileDto, String userId) {
        Profile existing = profileRepository.findByUserId(userId);
        if (existing != null) {
            throw new ProfileAlreadyExistsException(userId);
        }

        if (profileDto.preferences() != null) {
            domainService.validatePreferencesBusinessRules(profileDto.preferences());
        }

        CreateProfileDtoV1 sanitizedProfile = domainService.sanitizeProfileData(profileDto);

        Profile profile = createMapper.toEntity(sanitizedProfile);
        profile.setUserId(userId);

        Preferences preferences = preferencesService.findOrCreate(sanitizedProfile.preferences());
        if (preferences.getId() == null) {
            preferences = preferencesRepository.save(preferences);
        }
        profile.setPreferences(preferences);

        Profile savedProfile = profileRepository.save(profile);

        ProfileCreateEvent event = ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(savedProfile.getProfileId())
                .timestamp(Instant.now())
                .build();
        profileOutboxService.enqueueProfileCreated(event);

        log.info("Profile created successfully for userId: {}", userId);
        return savedProfile;
    }

    @Transactional
    public Profile update(CreateProfileDtoV1 profileDto, String userId) {
        Profile existingProfile = profileRepository.findByUserId(userId);
        if (existingProfile == null) {
            throw new ProfileNotFoundException(userId);
        }

        if (profileDto.preferences() != null) {
            domainService.validatePreferencesBusinessRules(profileDto.preferences());
        }

        CreateProfileDtoV1 sanitizedProfile = domainService.sanitizeProfileData(profileDto);

        // Detect field changes before applying the update
        Set<String> changedFields = detectChangedFields(existingProfile, sanitizedProfile);

        existingProfile.updateBasicInfo(
                sanitizedProfile.name(),
                sanitizedProfile.age(),
                sanitizedProfile.gender(),
                sanitizedProfile.bio(),
                sanitizedProfile.city()
        );

        Preferences oldPreferences = existingProfile.getPreferences();
        Preferences preferences = preferencesService.findOrCreate(sanitizedProfile.preferences());
        if (preferences.getId() == null) {
            preferences = preferencesRepository.save(preferences);
        }

        boolean preferencesChanged = !Objects.equals(oldPreferences, preferences);
        if (preferencesChanged) {
            changedFields.add("preferences");
            existingProfile.setPreferences(preferences);
        }

        Profile savedProfile = profileRepository.save(existingProfile);

        ChangeType changeType = domainService.determineChangeType(changedFields, preferencesChanged);
        enqueueProfileUpdatedEvent(savedProfile, changeType, changedFields);

        putInCache(savedProfile.getProfileId(), savedProfile);

        log.info("Profile updated successfully for userId: {} with changeType: {} and fields: {}",
                userId, changeType, changedFields);
        return savedProfile;
    }

    @Transactional
    public Profile patch(String userId, PatchProfileDto patchDto) {
        Profile existingProfile = profileRepository.findByUserId(userId);
        if (existingProfile == null) {
            throw new ProfileNotFoundException(userId);
        }

        if (!patchDto.hasAnyField()) {
            throw PatchOperationException.noFieldsProvided();
        }

        PatchProfileDto sanitizedPatch = domainService.sanitizePatchData(patchDto);

        // applyPatch() sets only non-null fields and returns which ones actually changed
        Set<String> changedFields = existingProfile.applyPatch(
                sanitizedPatch.name(),
                sanitizedPatch.age(),
                sanitizedPatch.gender(),
                sanitizedPatch.bio(),
                sanitizedPatch.city()
        );

        boolean preferencesChanged = false;
        if (sanitizedPatch.preferences() != null) {
            domainService.validatePreferencesBusinessRules(sanitizedPatch.preferences());

            Preferences oldPreferences = existingProfile.getPreferences();
            Preferences newPreferences = preferencesService.findOrCreate(sanitizedPatch.preferences());

            if (newPreferences.getId() == null) {
                newPreferences = preferencesRepository.save(newPreferences);
            }

            if (!Objects.equals(oldPreferences, newPreferences)) {
                existingProfile.setPreferences(newPreferences);
                preferencesChanged = true;
                changedFields.add("preferences");
            }
        }

        Profile savedProfile = profileRepository.save(existingProfile);

        ChangeType changeType = domainService.determineChangeType(changedFields, preferencesChanged);
        enqueueProfileUpdatedEvent(savedProfile, changeType, changedFields);

        putInCache(savedProfile.getProfileId(), savedProfile);

        log.info("Profile patched successfully for userId: {} with changeType: {} and fields: {}",
                userId, changeType, changedFields);
        return savedProfile;
    }

    @Transactional
    public Profile delete(String userId) {
        Profile profile = profileRepository.findByUserId(userId);
        UUID id = profile != null ? profile.getProfileId() : null;
        if (profile == null || profile.isDeleted()) {
            throw new ProfileNotFoundException(String.valueOf(id), "id");
        }
        if (domainService.canDeleteProfile(profile)) {
            profile.markAsDeleted();
            profileRepository.save(profile);
            evictFromCache(profile.getProfileId());
            log.info("Profile deleted successfully: {}", id);
        }

        ProfileDeleteEvent event = ProfileDeleteEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profile.getProfileId())
                .timestamp(Instant.now())
                .build();
        profileOutboxService.enqueueProfileDeleted(event);

        return profile;
    }

    @Transactional
    public void deleteMany(List<UUID> ids) {
        profileRepository.deleteAllById(ids);
        ids.forEach(this::evictFromCache);
    }

    // Cache management

    private void putInCache(UUID profileId, Profile profile) {
        resilientCacheManager.put(PROFILE_CACHE_NAME, profileId, profile);
    }

    private void evictFromCache(UUID profileId) {
        resilientCacheManager.evict(PROFILE_CACHE_NAME, profileId);
    }

    // Helper methods

    private Set<String> detectChangedFields(Profile existing, CreateProfileDtoV1 newDto) {
        Set<String> changed = new HashSet<>();
        if (!Objects.equals(existing.getName(), newDto.name())) changed.add("name");
        if (!Objects.equals(existing.getAge(), newDto.age())) changed.add("age");
        if (!Objects.equals(existing.getGender(), newDto.gender())) changed.add("gender");
        if (!Objects.equals(existing.getBio(), newDto.bio())) changed.add("bio");
        if (!Objects.equals(existing.getCity(), newDto.city())) changed.add("city");
        return changed;
    }

    private void enqueueProfileUpdatedEvent(Profile profile, ChangeType changeType, Set<String> changedFields) {
        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profile.getProfileId())
                .changeType(changeType)
                .changedFields(changedFields)
                .timestamp(Instant.now())
                .metadata(String.format("Profile updated: %s", changeType))
                .build();

        profileOutboxService.enqueueProfileUpdated(event);

        log.debug("Queued ProfileUpdatedEvent in outbox: eventId={}, profileId={}, changeType={}, fields={}",
                event.getEventId(), event.getProfileId(), changeType, changedFields);
    }
}
