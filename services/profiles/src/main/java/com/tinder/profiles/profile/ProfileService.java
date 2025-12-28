package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.security.InputSanitizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProfileService {

    private final ProfileRepository repo;
    private final PreferencesRepository preferencesRepository;
    private final CreateProfileMapper createMapper;
    private final GetProfileMapper getMapper;

    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final InputSanitizationService sanitizationService;


    public Page<Profile> getAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public GetProfileDto getOne(UUID id) {

        Cache.ValueWrapper profileCache = Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE")).get(id);
        if (profileCache != null) {
            Object cached = profileCache.get();

            if (cached instanceof Profile profile) {
                return getMapper.toGetProfileDto(profile);
            } else {
                String cachedType = (cached != null) ? cached.getClass().getName() : "null";
                throw new IllegalStateException("Invalid object type in cache for key " + id + ": " + cachedType);
            }
        }
        Optional<Profile> profileOptional = repo.findById(id);
        if (profileOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile with id " + id + " not found");
        }

        Profile profile = profileOptional.get();
        if (profile.isDeleted())
            return null;

        Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                .put(id, profile);
        return getMapper.toGetProfileDto(profile);

    }

    public Profile getByUsername(String username) {
        return repo.findByName(username);
    }

    public Profile getByUserId(String userId) {
        return repo.findByUserId(userId);
    }

    public Profile create(CreateProfileDtoV1 profile, String userId) {
        try {
            Profile existing = repo.findByUserId(userId);
            if (existing != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile for userId `%s` already exists".formatted(userId));
            }
            CreateProfileDtoV1 sanitizedProfile = getSanitizedProfile(profile);
            Profile profileEntity = createMapper.toEntity(sanitizedProfile);

            profileEntity.setUserId(userId);

            Preferences preferences = profileEntity.getPreferences();
            if (preferences != null && preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
                profileEntity.setPreferences(preferences);
            }

            Profile savedProfile = repo.save(profileEntity);

            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            return savedProfile;
        } catch (ResponseStatusException e) {
            log.error(e.getMessage());
            throw e;
        }

        catch (Exception e) {
            log.error("Error creating profile: " + e.getMessage());
            throw new RuntimeException(e);
        }

    }


    public Profile update(CreateProfileDtoV1 profile, String userId) {
        try {
            Profile existingProfile = repo.findByUserId(userId);
            if (existingProfile == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile for userId `%s` not found".formatted(userId));
            }

            CreateProfileDtoV1 sanitizedProfile = getSanitizedProfile(profile);

            // Update existing profile fields
            existingProfile.setName(sanitizedProfile.name());
            existingProfile.setAge(sanitizedProfile.age());
            existingProfile.setGender(sanitizedProfile.gender());
            existingProfile.setBio(sanitizedProfile.bio());
            existingProfile.setCity(sanitizedProfile.city());

            // Handle preferences update
            if (sanitizedProfile.preferences() != null) {
                Preferences preferences = existingProfile.getPreferences();
                if (preferences == null) {
                    preferences = new Preferences();
                    existingProfile.setPreferences(preferences);
                }

                PreferencesDto prefsDto = sanitizedProfile.preferences();
                preferences.setMinAge(prefsDto.getMinAge());
                preferences.setMaxAge(prefsDto.getMaxAge());
                preferences.setGender(prefsDto.getGender());

                if (preferences.getId() == null) {
                    preferences = preferencesRepository.save(preferences);
                    existingProfile.setPreferences(preferences);
                }
            }

            Profile savedProfile = repo.save(existingProfile);

            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            return savedProfile;
        } catch (ResponseStatusException e) {
            log.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating profile: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Profile patch(String id, JsonNode patchNode) throws IOException {
        try {
            Profile existingProfile = repo.findByUserId(id);
            if (existingProfile == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile for userId `%s` not found".formatted(id));
            }

            // Define allowed fields for patching (whitelist approach)
            Set<String> allowedFields = Set.of("name", "age", "gender", "bio", "city");

            // Validate that only allowed fields are being patched
            Iterator<String> fieldNames = patchNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (!allowedFields.contains(fieldName)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Field '%s' cannot be modified via PATCH. Allowed fields: %s"
                            .formatted(fieldName, allowedFields));
                }
            }

            // Apply patches to existing profile with sanitization
            if (patchNode.has("name")) {
                String name = patchNode.get("name").asText();
                if (name.length() < 2 || name.length() > 50) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "name must be between 2-50 characters");
                }
                existingProfile.setName(sanitizationService.sanitizePlainText(name));
            }

            if (patchNode.has("age")) {
                Integer age = patchNode.get("age").asInt();
                if (age < 18 || age > 130) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "age must be between 18-130");
                }
                existingProfile.setAge(age);
            }

            if (patchNode.has("gender")) {
                String gender = patchNode.get("gender").asText();
                if (!gender.matches("(?i)^(male|female|other)$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "gender must be male, female, or other");
                }
                existingProfile.setGender(sanitizationService.sanitizePlainText(gender));
            }

            if (patchNode.has("bio")) {
                String bio = patchNode.get("bio").asText();
                if (bio.length() > 1023) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "bio must be less than 1000 characters");
                }
                existingProfile.setBio(sanitizationService.sanitizePlainText(bio));
            }

            if (patchNode.has("city")) {
                String city = patchNode.get("city").asText();
                if (city.length() > 100) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "city name too long (max 100 characters)");
                }
                if (!city.matches("^[a-zA-ZÀ-ÿ\\s-]+$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "city can only contain letters, spaces, and hyphens");
                }
                existingProfile.setCity(sanitizationService.sanitizePlainText(city));
            }

            // Save updated profile
            Profile savedProfile = repo.save(existingProfile);

            // Update cache
            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            log.info("Profile patched successfully for userId: {}", id);
            return savedProfile;

        } catch (ResponseStatusException e) {
            log.error("Patch validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error patching profile for userId {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to patch profile", e);
        }
    }



    public Profile delete(UUID id) {
        Profile profile = repo.findById(id).orElse(null);
        if (profile != null) {

            profile.setDeleted(true);
            repo.save(profile);
            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .evict(profile.getProfileId());
        }
        return profile;
    }

    public void deleteMany(List<UUID> ids) {
        repo.deleteAllById(ids);
    }



    private @NonNull CreateProfileDtoV1 getSanitizedProfile(CreateProfileDtoV1 profile) {
        return new CreateProfileDtoV1(
                sanitizationService.sanitizePlainText(profile.name()),
                profile.age(),
                sanitizationService.sanitizePlainText(profile.gender()),
                sanitizationService.sanitizePlainText(profile.bio()),
                sanitizationService.sanitizePlainText(profile.city()),
                profile.preferences()
        );
    }
}
