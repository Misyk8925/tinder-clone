package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.ProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.profile.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProfileServiceImpl  {

    private final ProfileRepository repo;
    private final PreferencesRepository preferencesRepository;
    private final CreateProfileMapper mapper;
    private final GetProfileMapper getMapper;
    private final ProfileMapper profileMapper;

    private final ObjectMapper createMapper;
    private final CacheManager cacheManager;



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
                // Исправление NPE - проверяем на null перед вызовом getClass()
                String cachedType = (cached != null) ? cached.getClass().getName() : "null";
                throw new IllegalStateException("В кэше по ключу " + id + " лежит объект неверного типа: " + cachedType);
            }
        }
        Optional<Profile> profileOptional = repo.findById(id);
        if (profileOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль с id " + id + " не найден");
        }

        Profile profile = profileOptional.get();
        if (profile.isDeleted())
            return null;
        return getMapper.toGetProfileDto(profileOptional.get());

    }

    public Profile getByUsername(String username) {
        return repo.findByName(username);
    }

    public Profile getByProfileIdString(String profileIdString) {
        return repo.findByProfileIdString(profileIdString);
    }

    public Profile create(CreateProfileDtoV1 profile, String profileIdString) {
        try {
            Profile profileEntity = mapper.toEntity(profile);

            profileEntity.setProfileIdString(profileIdString);

            Preferences preferences = profileEntity.getPreferences();
            if (preferences != null && preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
                profileEntity.setPreferences(preferences);
            }
            Profile savedProfile = repo.save(profileEntity);

            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            return savedProfile;
        } catch (Exception e) {
            log.error("Error creating profile: " + e.getMessage());
            throw new RuntimeException(e);
        }

    }

    public Profile update(UUID id, CreateProfileDtoV1 profile) {
        if (repo.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id));
        }
        try {
            Profile profileEntity = mapper.toEntity(profile);

            profileEntity.setProfileId(id);

            Preferences preferences = profileEntity.getPreferences();
            if (preferences != null && preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
                profileEntity.setPreferences(preferences);
            }
            Profile savedProfile = repo.save(profileEntity);


            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            return savedProfile;
        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    public Profile patch(UUID id, JsonNode patchNode) throws IOException {
        Profile profile = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));

        createMapper.readerForUpdating(profile).readValue(patchNode);

        return repo.save(profile);
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

    public List<ProfileDto> fetchPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return repo.findAll(pageable).stream()
                .map(profileMapper::toDto)
                .toList();
    }

    public List<GetProfileDto> searchByViewerPrefs(UUID viewerId, PreferencesDto p, int limit) {

        Profile viewer = repo.findById(viewerId)
                .orElseThrow(() -> new NoSuchElementException("viewer not found"));

         List<Profile> base = repo.findAll(PageRequest.of(0, Math.max(limit, 1))).stream()

                .filter(profile -> !profile.isDeleted())
                .filter(profile -> {
                    Integer age = profile.getAge();
                    return age >= p.getMinAge() && age <= p.getMaxAge();
                })
                .filter(profile -> p.getGender() == null || p.getGender().isEmpty() ||
                        p.getGender().equalsIgnoreCase("any"))
                .limit(limit)
                .toList();

        return base.stream().map(getMapper::toGetProfileDto).toList();
    }


    public List<GetProfileDto> getMany(List<UUID> ids) {
        return repo.findAllById(ids).stream()
                .map(getMapper::toGetProfileDto)
                .toList();
    }

    public List<GetProfileDto> getActiveUsers() {
        return repo.findAllByIsDeletedFalse().stream()
                .map(getMapper::toGetProfileDto)
                .toList();
    }
}
