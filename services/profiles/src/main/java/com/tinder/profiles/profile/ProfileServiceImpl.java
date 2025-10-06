package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.location.LocationService;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.preferences.PreferencesService;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
@Service
public class ProfileServiceImpl  {

    private final ProfileRepository repo;
    private final LocationService locationService;
    private final PreferencesService preferencesService;
    private final PreferencesRepository preferencesRepository;
    private final CreateProfileMapper mapper;
    private final GetProfileMapper getMapper;

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

                throw new IllegalStateException("В кэше по ключу " + id + " лежит объект неверного типа: " + cached.getClass());
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

    public Profile create(CreateProfileDtoV1 profile) {
        try {
            Profile profileEntity = mapper.toEntity(profile);

            Preferences preferences = profileEntity.getPreferences();
            if (preferences != null && preferences.getId() == null) {
                preferences = preferencesRepository.save(preferences);
                profileEntity.setPreferences(preferences);
            }
            Profile savedProfile = repo.save(profileEntity);

            System.out.println(savedProfile.getProfileId());

            System.out.println(savedProfile.getClass().getName());
            Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE"))
                    .put(savedProfile.getProfileId(), savedProfile);

            return savedProfile;
        } catch (Exception e) {

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
            // for soft delete
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

}
