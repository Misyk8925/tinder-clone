package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.location.LocationService;
import com.tinder.profiles.preferences.PreferencesService;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import lombok.RequiredArgsConstructor;
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
    private final CreateProfileMapper mapper;
    private final GetProfileMapper getMapper;

    private final ObjectMapper createMapper;
    private final CacheManager cacheManager;


    public Page<Profile> getAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    @Cacheable(value = "PROFILE_CACHE", key = "#result.profileId()")
    public GetProfileDto getOne(UUID id) {

        try {
            Optional<Profile> profileOptional = repo.findById(id);
            return getMapper.toGetProfileDto(profileOptional.get());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public Profile getByUsername(String username) {
        return repo.findByName(username);
    }

    public List<Profile> getMany(List<UUID> ids) {
        return repo.findAllById(ids);
    }

    public Profile create(CreateProfileDtoV1 profile) {

        try {
            Profile profileEntity = mapper.toEntity(profile);

            Profile savedProfile = repo.save(profileEntity);


            Objects.requireNonNull(cacheManager.getCache("PROFILE_CACHE"))
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

    public List<UUID> patchMany(List<UUID> ids, JsonNode patchNode) throws IOException {
        Collection<Profile> profiles = repo.findAllById(ids);

        for (Profile profile : profiles) {
            createMapper.readerForUpdating(profile).readValue(patchNode);
        }

        List<Profile> resultProfiles = repo.saveAll(profiles);
        return resultProfiles.stream()
                .map(Profile::getProfileId)
                .toList();
    }

    @CacheEvict(value = "PROFILE_CACHE", key = "#profileId()")
    public Profile delete(UUID id) {
        Profile profile = repo.findById(id).orElse(null);
        if (profile != null) {
            repo.delete(profile);
        }
        return profile;
    }

    public void deleteMany(List<UUID> ids) {
        repo.deleteAllById(ids);
    }

//    @Override
//    public Profile createProfile(ProfileDto profileDto) {
//        UUID id = UUID.randomUUID();
//        Location location = locationService.create(profileDto.getCity());
//
//    }
}
