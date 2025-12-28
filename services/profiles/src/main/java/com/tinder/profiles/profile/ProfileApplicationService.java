package com.tinder.profiles.profile;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Application service orchestrating profile operations.
 * Handles transactions, caching, and coordinates between domain service and infrastructure.
 */
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

    private static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";

    /**
     * Get all profiles with pagination
     */
    public Page<Profile> getAll(Pageable pageable) {
        return profileRepository.findAll(pageable);
    }

    /**
     * Get profile by ID with caching
     */
    public GetProfileDto getOne(UUID id) {
        // Try to get from cache first
        try {
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
                    throw new IllegalStateException("Invalid object type in cache for key " + id + ": " + cachedType);
                }

            }

            // Load from database
            Optional<Profile> profileOptional = profileRepository.findById(id);
            if (profileOptional.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile with id " + id + " not found");
            }

            Profile profile = profileOptional.get();
            if (profile.isDeleted()) {
                return null;
            }

            // Cache the profile
            putInCache(id, profile);
            return getMapper.toGetProfileDto(profile);
        }
         catch (NullPointerException e) {
            log.error("Cache not found: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cache not found", e);
        }
        catch (IllegalStateException e) {
            log.error("Cache retrieval error for profile {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cache retrieval error", e);
        }
    }

    /**
     * Get profile by username
     */
    public Profile getByUsername(String username) {
        return profileRepository.findByName(username);
    }

    /**
     * Get profile by user ID
     */
    public Profile getByUserId(String userId) {
        return profileRepository.findByUserId(userId);
    }

    /**
     * Create a new profile
     */
    @Transactional
    public Profile create(CreateProfileDtoV1 profileDto, String userId) {
        try {
            // Check if profile already exists
            Profile existing = profileRepository.findByUserId(userId);
            if (existing != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profile for userId `%s` already exists".formatted(userId));
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
            Preferences preferences = domainService.updateOrCreatePreferences(profile, sanitizedProfile.preferences());
            if (preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
            }
            profile.setPreferences(preferences);

            // Save profile
            Profile savedProfile = profileRepository.save(profile);

            log.info("Profile created successfully for userId: {}", userId);
            return savedProfile;

        } catch (ResponseStatusException e) {
            log.error("Validation error creating profile: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating profile for userId {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create profile", e);
        }
    }



    /**
     * Update existing profile
     */
    @Transactional
    public Profile update(CreateProfileDtoV1 profileDto, String userId) {
        try {
            // Find existing profile
            Profile existingProfile = profileRepository.findByUserId(userId);
            if (existingProfile == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Profile for userId `%s` not found".formatted(userId));
            }

            // Validate cross-field business rules (Bean Validation handles basic constraints)
            if (profileDto.preferences() != null) {
                domainService.validatePreferencesBusinessRules(profileDto.preferences());
            }

            // Sanitize input data
            CreateProfileDtoV1 sanitizedProfile = domainService.sanitizeProfileData(profileDto);

            // Update using domain service
            domainService.updateProfileFromDto(existingProfile, sanitizedProfile);

            // Handle preferences update
            Preferences preferences = domainService.updateOrCreatePreferences(existingProfile, sanitizedProfile.preferences());
            if (preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
            }
            existingProfile.setPreferences(preferences);

            // Save
            Profile savedProfile = profileRepository.save(existingProfile);

            // Update cache
            putInCache(savedProfile.getProfileId(), savedProfile);

            log.info("Profile updated successfully for userId: {}", userId);
            return savedProfile;

        } catch (ResponseStatusException e) {
            log.error("Validation error updating profile: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating profile for userId {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update profile", e);
        }
    }

    /**
     * Patch profile with partial updates
     */
    @Transactional
    public Profile patch(String userId, PatchProfileDto patchDto) {
        try {
            Profile existingProfile = profileRepository.findByUserId(userId);
            if (existingProfile == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Profile for userId `%s` not found".formatted(userId));
            }

            // Check if at least one field is provided
            if (!patchDto.hasAnyField()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one field must be provided for update");
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

            // Save updated profile
            Profile savedProfile = profileRepository.save(existingProfile);

            // Update cache
            putInCache(savedProfile.getProfileId(), savedProfile);

            log.info("Profile patched successfully for userId: {}", userId);
            return savedProfile;

        } catch (ResponseStatusException e) {
            log.error("Patch validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error patching profile for userId {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to patch profile", e);
        }
    }

    /**
     * Delete profile (soft delete)
     */
    @Transactional
    public Profile delete(UUID id) {
        Profile profile = profileRepository.findById(id).orElse(null);
        if (profile != null) {
            if (domainService.canDeleteProfile(profile)) {
                domainService.markAsDeleted(profile);
                profileRepository.save(profile);
                evictFromCache(profile.getProfileId());
                log.info("Profile deleted successfully: {}", id);
            }
        }
        return profile;
    }

    /**
     * Delete multiple profiles
     */
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

