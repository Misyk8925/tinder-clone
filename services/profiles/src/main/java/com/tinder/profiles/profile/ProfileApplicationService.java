package com.tinder.profiles.profile;

import com.tinder.profiles.kafka.ProfileEventProducer;
import com.tinder.profiles.kafka.dto.ChangeType;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
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
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;
    private final InputSanitizationService sanitizationService;
    private final PreferencesService preferencesService;
    private final ProfileEventProducer profileEventProducer;

    @Value("${kafka.topics.profile-events.updated}")
    private String profileUpdatedEventsTopic;

    @Value("${kafka.topics.profile-events.created}")
    private String profileCreatedEventsTopic;

    @Value("${kafka.topics.profile-events.deleted}")
    private String profileDeletedEventsTopic;


    private static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";


    public Page<Profile> getAll(Pageable pageable) {
        return profileRepository.findAll(pageable);
    }

    public GetProfileDto getOne(UUID id) {
        try {
            // Try to get from cache first
            Cache.ValueWrapper profileCache = Objects.requireNonNull(cacheManager.getCache(PROFILE_CACHE_NAME)).get(id);
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

            // Load from database
            Optional<Profile> profileOptional = profileRepository.findById(id);
            if (profileOptional.isEmpty()) {
                throw new ProfileNotFoundException(id.toString(), "id");
            }

            Profile profile = profileOptional.get();
            if (profile.isDeleted()) {
                return null;
            }

            // Cache the profile
            putInCache(id, profile);
            return getMapper.toGetProfileDto(profile);

        } catch (NullPointerException e) {
            log.error("Cache not initialized: {}", e.getMessage());
            // Continue without cache - load from database
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
        // Check if profile already exists
        Profile existing = profileRepository.findByUserId(userId);
        if (existing != null) {
            throw new ProfileAlreadyExistsException(userId);
        }

        // Validate cross-field business rules (Bean Validation handles basic constraints)
        if (profileDto.preferences() != null) {
            domainService.validatePreferencesBusinessRules(profileDto.preferences());
        }

        // Sanitize input data
        CreateProfileDtoV1 sanitizedProfile = domainService.sanitizeProfileData(profileDto);

        // Map to entity
        Profile profile = createMapper.toEntity(sanitizedProfile);
        profile.setUserId(userId);

        // Handle preferences
        Preferences preferences = preferencesService.findOrCreate(sanitizedProfile.preferences());
        if (preferences.getId() == null) {
            preferences = preferencesRepository.save(preferences);
        }
        profile.setPreferences(preferences);


        Profile savedProfile = profileRepository.save(profile);

        try {
            // Send profile created event
            profileEventProducer.sendProfileCreateEvent(
                    ProfileCreateEvent.builder()
                            .eventId(UUID.randomUUID())
                            .profileId(savedProfile.getProfileId())
                            .timestamp(Instant.now())
                            .build(),
                    savedProfile.getProfileId().toString(),
                    profileCreatedEventsTopic
            );
        } catch (Exception e) {
            log.error("Failed to send ProfileCreateEvent for profile {}: {}",
                    savedProfile.getProfileId(), e.getMessage(), e);
            // Don't throw - event sending should not fail the create operation
        }

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

        // Track changed fields before update
        Set<String> changedFields = detectChangedFields(existingProfile, sanitizedProfile);
        boolean preferencesChanged = false;

        // Update profile fields
        domainService.updateProfileFromDto(existingProfile, sanitizedProfile);

        // Handle preferences
        Preferences oldPreferences = existingProfile.getPreferences();
        Preferences preferences = preferencesService.findOrCreate(sanitizedProfile.preferences());
        if (preferences.getId() == null) {
            preferences = preferencesRepository.save(preferences);
        }

        // Check if preferences changed
        if (!preferencesEqual(oldPreferences, preferences)) {
            preferencesChanged = true;
            changedFields.add("preferences");
        }

        existingProfile.setPreferences(preferences);

        Profile savedProfile = profileRepository.save(existingProfile);

        // Determine change type and send event
        ChangeType changeType = determineChangeType(changedFields, preferencesChanged);
        sendProfileUpdatedEvent(savedProfile, changeType, changedFields);

        // Update cache
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

        // Check if at least one field is provided
        if (!patchDto.hasAnyField()) {
            throw PatchOperationException.noFieldsProvided();
        }

        // Track changed fields
        Set<String> changedFields = new HashSet<>();
        boolean preferencesChanged = false;

        // Apply patches (Bean Validation already validated the format)
        if (patchDto.name() != null) {
            existingProfile.setName(sanitizationService.sanitizePlainText(patchDto.name()));
            changedFields.add("name");
        }

        if (patchDto.age() != null) {
            existingProfile.setAge(patchDto.age());
            changedFields.add("age");
        }

        if (patchDto.gender() != null) {
            existingProfile.setGender(sanitizationService.sanitizePlainText(patchDto.gender()));
            changedFields.add("gender");
        }

        if (patchDto.bio() != null) {
            existingProfile.setBio(sanitizationService.sanitizePlainText(patchDto.bio()));
            changedFields.add("bio");
        }

        if (patchDto.city() != null) {
            existingProfile.setCity(sanitizationService.sanitizePlainText(patchDto.city()));
            changedFields.add("city");
        }

        // Handle preferences update
        if (patchDto.preferences() != null) {
            domainService.validatePreferencesBusinessRules(patchDto.preferences());

            Preferences oldPreferences = existingProfile.getPreferences();
            Preferences newPreferences = preferencesService.findOrCreate(patchDto.preferences());

            if (newPreferences.getId() == null) {
                newPreferences = preferencesRepository.save(newPreferences);
            }

            // Check if preferences actually changed
            if (!preferencesEqual(oldPreferences, newPreferences)) {
                existingProfile.setPreferences(newPreferences);
                preferencesChanged = true;
                changedFields.add("preferences");
            }
        }

        Profile savedProfile = profileRepository.save(existingProfile);

        // Determine change type and send event
        ChangeType changeType = determineChangeType(changedFields, preferencesChanged);
        sendProfileUpdatedEvent(savedProfile, changeType, changedFields);

        // Update cache
        putInCache(savedProfile.getProfileId(), savedProfile);

        log.info("Profile patched successfully for userId: {} with changeType: {} and fields: {}",
                userId, changeType, changedFields);
        return savedProfile;
    }

    @Transactional
    public Profile delete( String userId) {
        Profile profile = profileRepository.findByUserId(userId);
        UUID id = profile != null ? profile.getProfileId() : null;
        if (profile == null || profile.isDeleted()) {
            throw new ProfileNotFoundException(String.valueOf(id), "id");
        }
        if (domainService.canDeleteProfile(profile)) {
            domainService.markAsDeleted(profile);
            profileRepository.save(profile);
            evictFromCache(profile.getProfileId());
            log.info("Profile deleted successfully: {}", id);
        }

        try {
            // Send profile deleted event
            profileEventProducer.sendProfileDeleteEvent(
                    ProfileDeleteEvent.builder()
                            .eventId(UUID.randomUUID())
                            .profileId(profile.getProfileId())
                            .timestamp(Instant.now())
                            .build(),
                    profile.getProfileId().toString(),
                    profileDeletedEventsTopic
            );
        } catch (Exception e) {
            log.error("Failed to send ProfileDeleteEvent for profile {}: {}",
                    profile.getProfileId(), e.getMessage(), e);
            // Don't throw - event sending should not fail the delete operation
        }
        return profile;
    }

    @Transactional
    public void deleteMany(List<UUID> ids) {
        profileRepository.deleteAllById(ids);
        ids.forEach(this::evictFromCache);
    }

    // Cache management methods

    private void putInCache(UUID profileId, Profile profile) {
        Objects.requireNonNull(cacheManager.getCache(PROFILE_CACHE_NAME))
                .put(profileId, profile);
    }

    private void evictFromCache(UUID profileId) {
        Objects.requireNonNull(cacheManager.getCache(PROFILE_CACHE_NAME))
                .evict(profileId);
    }

    // Event handling helper methods

    /**
     * Detect which fields changed between existing profile and new DTO
     */
    private Set<String> detectChangedFields(Profile existing, CreateProfileDtoV1 newDto) {
        Set<String> changed = new HashSet<>();

        if (!Objects.equals(existing.getName(), newDto.name())) {
            changed.add("name");
        }
        if (!Objects.equals(existing.getAge(), newDto.age())) {
            changed.add("age");
        }
        if (!Objects.equals(existing.getGender(), newDto.gender())) {
            changed.add("gender");
        }
        if (!Objects.equals(existing.getBio(), newDto.bio())) {
            changed.add("bio");
        }
        if (!Objects.equals(existing.getCity(), newDto.city())) {
            changed.add("city");
        }

        return changed;
    }

    /**
     * Check if preferences are equal
     */
    private boolean preferencesEqual(Preferences old, Preferences newPrefs) {
        if (old == null && newPrefs == null) {
            return true;
        }
        if (old == null || newPrefs == null) {
            return false;
        }

        return Objects.equals(old.getMinAge(), newPrefs.getMinAge())
                && Objects.equals(old.getMaxAge(), newPrefs.getMaxAge())
                && Objects.equals(old.getGender(), newPrefs.getGender())
                && Objects.equals(old.getMaxRange(), newPrefs.getMaxRange());
    }

    /**
     * Determine the type of change based on changed fields
     *
     * Priority order:
     * 1. PREFERENCES - if preferences changed
     * 2. CRITICAL_FIELDS - if age, gender, or city changed
     * 3. NON_CRITICAL - for name, bio changes
     */
    private ChangeType determineChangeType(Set<String> changedFields, boolean preferencesChanged) {
        if (preferencesChanged) {
            return ChangeType.PREFERENCES;
        }

        // Critical fields that affect matching
        Set<String> criticalFields = Set.of("age", "gender", "city");
        for (String field : changedFields) {
            if (criticalFields.contains(field)) {
                return ChangeType.CRITICAL_FIELDS;
            }
        }

        return ChangeType.NON_CRITICAL;
    }

    /**
     * Send profile updated event to Kafka
     */
    private void sendProfileUpdatedEvent(Profile profile, ChangeType changeType, Set<String> changedFields) {
        try {
            ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .profileId(profile.getProfileId())
                    .changeType(changeType)
                    .changedFields(changedFields)
                    .timestamp(Instant.now())
                    .metadata(String.format("Profile updated: %s", changeType))
                    .build();

            profileEventProducer.sendProfileUpdateEvent(
                    event,
                    profile.getProfileId().toString(),
                    profileUpdatedEventsTopic
            );

            log.debug("Sent ProfileUpdatedEvent: eventId={}, profileId={}, changeType={}, fields={}",
                    event.getEventId(), event.getProfileId(), changeType, changedFields);

        } catch (Exception e) {
            log.error("Failed to send ProfileUpdatedEvent for profile {}: {}",
                    profile.getProfileId(), e.getMessage(), e);
            // Don't throw - event sending should not fail the update operation
        }
    }
}

