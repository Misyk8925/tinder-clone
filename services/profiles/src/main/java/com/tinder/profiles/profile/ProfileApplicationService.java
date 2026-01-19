package com.tinder.profiles.profile;

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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        domainService.updateProfileFromDto(existingProfile, sanitizedProfile);


        Preferences preferences = preferencesService.findOrCreate(sanitizedProfile.preferences());
        if (preferences.getId() == null) {
            preferences = preferencesRepository.save(preferences);
        }
        existingProfile.setPreferences(preferences);

        Profile savedProfile = profileRepository.save(existingProfile);

        // Update cache
        putInCache(savedProfile.getProfileId(), savedProfile);

        log.info("Profile updated successfully for userId: {}", userId);
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

        // Apply patches (Bean Validation already validated the format)
        if (patchDto.name() != null) {
            existingProfile.setName(sanitizationService.sanitizePlainText(patchDto.name()));
        }

        if (patchDto.age() != null) {
            existingProfile.setAge(patchDto.age());
        }

        if (patchDto.gender() != null) {
            existingProfile.setGender(sanitizationService.sanitizePlainText(patchDto.gender()));
        }

        if (patchDto.bio() != null) {
            existingProfile.setBio(sanitizationService.sanitizePlainText(patchDto.bio()));
        }

        if (patchDto.city() != null) {
            existingProfile.setCity(sanitizationService.sanitizePlainText(patchDto.city()));
        }


        Profile savedProfile = profileRepository.save(existingProfile);


        putInCache(savedProfile.getProfileId(), savedProfile);

        log.info("Profile patched successfully for userId: {}", userId);
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


}

